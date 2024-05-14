package io.github.workload.overloading;

import io.github.workload.HyperParameter;
import io.github.workload.WorkloadPriority;
import io.github.workload.annotations.ThreadSafe;
import io.github.workload.annotations.VisibleForTesting;
import io.github.workload.metrics.tumbling.CountAndTimeRolloverStrategy;
import io.github.workload.metrics.tumbling.CountAndTimeWindowState;
import io.github.workload.metrics.tumbling.TumblingWindow;
import io.github.workload.metrics.tumbling.WindowConfig;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * How to shed excess workload based on {@link WorkloadPriority}.
 */
@Slf4j
@ThreadSafe
abstract class WorkloadShedder {
    static final double GRADIENT_IDLE = 1.0d;
    static final double GRADIENT_BUSIEST = 0.5d;

    static final double OVER_SHED_BOUND = HyperParameter.getDouble(Heuristic.OVER_SHED_BOUND, 1.01d);
    static final double OVER_ADMIT_BOUND = HyperParameter.getDouble(Heuristic.OVER_ADMIT_BOUND, 0.5d); // TODO
    static final double DROP_RATE = HyperParameter.getDouble(Heuristic.SHED_DROP_RATE, 0.05d);
    static final double RECOVER_RATE = HyperParameter.getDouble(Heuristic.SHED_RECOVER_RATE, 0.015d);

    protected final String name;
    private final TumblingWindow<CountAndTimeWindowState> window;

    /**
     * 准入等级水位线/准入门槛，其优先级越高则准入控制越严格，即门槛越高.
     */
    private final AtomicReference<WorkloadPriority> watermark = new AtomicReference<>(WorkloadPriority.ofLowest());

    /**
     * 计算过载梯度值：[{@link #GRADIENT_BUSIEST}, {@link #GRADIENT_IDLE}].
     *
     * @param nowNs    当前系统时间，in nano second
     * @param snapshot 上一个窗口的状态快照
     * @return 为1时不过载；小于1说明过载，值越小表示过载越严重，最小0.5
     */
    protected abstract double overloadGradient(long nowNs, CountAndTimeWindowState snapshot);

    protected WorkloadShedder(String name) {
        this.name = name;
        WindowConfig<CountAndTimeWindowState> config = WindowConfig.create(
                new CountAndTimeRolloverStrategy() {
                    @Override
                    public void onRollover(long nowNs, CountAndTimeWindowState snapshot, TumblingWindow<CountAndTimeWindowState> window) {
                        predictWatermark(snapshot, overloadGradient(nowNs, snapshot));
                    }
                }
        );
        this.window = new TumblingWindow<>(config, name, System.nanoTime());
    }

    boolean admit(@NonNull WorkloadPriority priority) {
        boolean admitted = satisfyWatermark(priority);
        window.advance(priority, admitted, System.nanoTime());
        return admitted;
    }

    WorkloadPriority watermark() {
        return watermark.get();
    }

    @VisibleForTesting
    void predictWatermark(CountAndTimeWindowState lastWindow, double gradient) {
        // TODO Enhanced logic to consider broader data history for watermark adaptation
        log.debug("[{}] total:{}, admit:{}, shed:{}", name, lastWindow.requested(), lastWindow.admitted(), lastWindow.shedded());
        if (isOverloaded(gradient)) {
            shedMore(lastWindow, gradient);
        } else {
            admitMore(lastWindow, gradient);
        }
    }

    protected final boolean isOverloaded(double gradient) {
        return gradient < GRADIENT_IDLE;
    }

    // penalize low priority workloads
    private void shedMore(CountAndTimeWindowState lastWindow, double gradient) {
        // 确保在精确提高 watermark 时，不会因为过度抛弃低优先级请求而影响服务的整体可用性，尽可能保持高 goodput
        final int admitted = lastWindow.admitted();
        final int requested = lastWindow.requested();
        final int expectedToDrop = (int) (DROP_RATE * admitted);
        if (expectedToDrop == 0) {
            // 上个周期的准入量太少，无法决策抛弃哪个 TODO 不调整watermark？会不会负载已经恢复正常但新请求一直被拒绝？
            log.info("[{}] unable to shed more: too few window admitted {}/{}", name, admitted, requested);
            return;
        }

        final WorkloadPriority currentWatermark = watermark();
        final int currentP = currentWatermark.P();
        final ConcurrentSkipListMap<Integer, AtomicInteger> histogram = lastWindow.histogram();
        int accumulatedToDrop = 0;
        // histogram: (6, 3) -> (9, 10) -> ... -> (112, 2) -> (195, 11) -> (1894, 3)
        // expectedDropNextCycle:4
        // watermark.P=1999/1894, 要切换到112，但195是过度抛弃了，errorRate=(11+3-4)/4=10/4=2.5
        // TODO should we respect currentP?
        final Iterator<Map.Entry<Integer, AtomicInteger>> descendingEntries = histogram.headMap(currentP, true).descendingMap().entrySet().iterator();
        if (!descendingEntries.hasNext()) {
            log.warn("[{}] P:{} beyond histogram, nothing to shed", name, currentP);
            return;
        }

        while (descendingEntries.hasNext()) {
            final Map.Entry<Integer, AtomicInteger> entry = descendingEntries.next();
            final int candidateP = entry.getKey();
            final int candidateRequested = entry.getValue().get();
            accumulatedToDrop += candidateRequested;
            if (log.isDebugEnabled()) {
                log.debug("[{}] shed candidate(P:{} requested:{}), window admitted:{}, accumulated:{}/{}",
                        name, candidateP, candidateRequested, admitted, accumulatedToDrop, expectedToDrop);
            }

            if (accumulatedToDrop >= expectedToDrop) {
                double errorRate = (double) (accumulatedToDrop - expectedToDrop) / expectedToDrop;
                int targetP;
                if (descendingEntries.hasNext() && errorRate < OVER_SHED_BOUND) {
                    // 误差率可接受，and candidate is not head, candidate will shed workload
                    targetP = descendingEntries.next().getKey();
                    log.warn("[{}] shed more({}/{}), error:{}, window admitted:{}, {} -> {}", name, accumulatedToDrop, expectedToDrop, errorRate, admitted, currentWatermark.simpleString(), targetP);
                } else {
                    targetP = candidateP; // this candidate wil not shed workload
                    // 备选项并没有被shed，提前加上去的退回来
                    accumulatedToDrop -= candidateRequested;
                    // errRate might be negative: below expectation
                    errorRate = (double) (accumulatedToDrop - expectedToDrop) / expectedToDrop;
                    log.warn("[{}] degraded shed more({}/{}), error:{}, window admitted:{}, {} -> {}", name, accumulatedToDrop, expectedToDrop, errorRate, admitted, currentWatermark.simpleString(), targetP);
                }
                watermark.updateAndGet(curr -> curr.deriveFrom(targetP));
                log.info("[{}] Updating watermark for shedMore: {} -> {}", name, currentWatermark.simpleString(), watermark().simpleString());
                return;
            }

            if (!descendingEntries.hasNext()) {
                // 还不够扣呢，但已经没有可扣的了：we should never shed all
                watermark.updateAndGet(curr -> curr.deriveFrom(candidateP));
                log.info("[{}] Updating watermark for shedMore: {} -> {}", name, currentWatermark.simpleString(), watermark().simpleString());
                return;
            }
        }
    }

    // reward low priority workloads
    private void admitMore(CountAndTimeWindowState lastWindow, double gradient) {
        final WorkloadPriority currentWatermark = watermark();
        final int currentP = currentWatermark.P();
        if (WorkloadPriority.MAX_P == currentP) {
            return;
        }

        final int admitted = lastWindow.admitted();
        final int requested = lastWindow.requested();
        final int expectedToAdmit = (int) (RECOVER_RATE * admitted);
        if (expectedToAdmit == 0) {
            watermark.set(WorkloadPriority.ofLowest());
            log.info("[{}] idle window admit all: {}/{}", name, admitted, requested);
            return;
        }

        int accumulatedToAdmit = 0;
        final Iterator<Map.Entry<Integer, AtomicInteger>> ascendingP = lastWindow.histogram().tailMap(currentP, false).entrySet().iterator();
        if (!ascendingP.hasNext()) {
            watermark.set(WorkloadPriority.ofLowest());
            log.info("[{}] beyond tail of histogram, admit all: {}/{}", name, admitted, requested);
            return;
        }

        while (ascendingP.hasNext()) {
            final Map.Entry<Integer, AtomicInteger> entry = ascendingP.next();
            final int candidateP = entry.getKey();
            final int candidateRequested = entry.getValue().get();
            accumulatedToAdmit += candidateRequested;
            if (log.isDebugEnabled()) {
                log.debug("[{}] admit candidate(P:{} requested:{}), window admitted:{}, accumulated:{}/{}", name, candidateP, candidateRequested, admitted, accumulatedToAdmit, expectedToAdmit);
            }

            if (accumulatedToAdmit >= expectedToAdmit) { // TODO error rate
                watermark.updateAndGet(curr -> curr.deriveFrom(candidateP));
                log.info("[{}] Updating watermark for admitMore: {} -> {}", name, currentWatermark.simpleString(), watermark().simpleString());
                return;
            }

            if (!ascendingP.hasNext()) {
                log.warn("[{}] histogram tail reached but still not enough for admit more: happy to admit all", name);
                watermark.set(WorkloadPriority.ofLowest());
                return;
            }
        }
    }

    protected final CountAndTimeWindowState currentWindow() {
        return window.current();
    }

    protected final WindowConfig<CountAndTimeWindowState> windowConfig() {
        return window.getConfig();
    }

    private boolean satisfyWatermark(WorkloadPriority priority) {
        // 在水位线以下的请求放行
        return priority.P() <= watermark().P();
    }

    @VisibleForTesting
    void resetForTesting() {
        this.window.resetForTesting();
        this.watermark.set(WorkloadPriority.ofLowest());
        log.debug("[{}] WorkloadShedder has been reset for testing purposes.", name);
    }
}
