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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * How to shed excess workload based on {@link WorkloadPriority}.
 *
 * <p>shed：削减</p>
 */
@Slf4j
@ThreadSafe
abstract class FairShedder {
    static final double GRADIENT_IDLE = 1.2d;
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
     * <p>负载反馈因子.</p>
     *
     * @param nowNs    当前系统时间，in nano second
     * @param snapshot 上一个窗口的状态快照
     * @return 为1时不过载；小于1说明过载，值越小表示过载越严重
     */
    protected abstract double overloadGradient(long nowNs, CountAndTimeWindowState snapshot);

    protected FairShedder(String name) {
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
        // TODO Enhanced logic to consider broader data history for watermark prediction
        log.debug("[{}] predict with lastWindow total:{}, admit:{}, shed:{}, gradient:{}", name, lastWindow.requested(), lastWindow.admitted(), lastWindow.shedded(), gradient);
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
    // 确保在精确提高 watermark 时，不会因为过度抛弃低优先级请求而影响服务的整体可用性，尽可能保持高 goodput
    private void shedMore(CountAndTimeWindowState lastWindow, double gradient) {
        final int admitted = lastWindow.admitted();
        final int requested = lastWindow.requested();
        final int targetDrop = (int) (DROP_RATE * admitted);
        final WorkloadPriority currentWatermark = watermark();
        if (targetDrop == 0) {
            log.info("[{}] cannot shedMore: lastWindow too few admitted {}/{}, watermark {}, grad:{}", name, admitted, requested, currentWatermark.simpleString(), gradient);
            return;
        }

        int accDrop = 0; // accumulated drop count
        final Iterator<Map.Entry<Integer, AtomicInteger>> higherPriorities = lastWindow.histogram().headMap(currentWatermark.P(), true).descendingMap().entrySet().iterator();
        if (!higherPriorities.hasNext()) {
            // e,g. watermark目前是112，last window histogram：[(150, 8), (149, 2), (132, 10), ..., (115, 9)]
            // 实际上，这些低优先级的请求在上个窗口已经全部被shed了，当前准入门槛已经足够高了，无需调整
            // 类死锁问题：它一直shed？等admitMore来解锁
            log.info("[{}] need not shedMore: already shed enough, watermark {}, grad:{}", name, currentWatermark.simpleString(), gradient);
            return;
        }

        int round = 0;
        while (higherPriorities.hasNext()) {
            final Map.Entry<Integer, AtomicInteger> entry = higherPriorities.next();
            final int candidateP = entry.getKey(); // WorkloadPriority#P
            final int candidateR = entry.getValue().get(); // 该P在上个周期被请求次数：包括被drop量
            accDrop += candidateR;
            round++;
            if (accDrop >= targetDrop) {
                double errorRate = (double) (accDrop - targetDrop) / targetDrop;
                int targetP;
                if (higherPriorities.hasNext() && errorRate < OVER_SHED_BOUND) {
                    // not overly shed and candidate is not head: shed candidate(inclusive) workload
                    targetP = higherPriorities.next().getKey();
                } else {
                    targetP = candidateP; // this candidate wil not shed workload
                    // 备选项并没有被shed，提前加上去的退回来
                    accDrop -= candidateR;
                    errorRate = (double) (accDrop - targetDrop) / targetDrop;
                }
                watermark.updateAndGet(curr -> curr.deriveFrom(targetP));
                log.info("[{}] grad:{}, round:{} shedMore watermark: {} -> {}, drop {}/{} err:{}", name, gradient, round, currentWatermark.simpleString(), watermark().simpleString(), accDrop, targetDrop, errorRate);
                return;
            }

            if (!higherPriorities.hasNext()) {
                watermark.updateAndGet(curr -> curr.deriveFrom(candidateP));
                log.info("[{}] grad:{}, round:{} shedMore watermark: {} -> {}", name, gradient, round, currentWatermark.simpleString(), watermark().simpleString());
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
        final int targetAdmit = (int) (RECOVER_RATE * admitted);
        if (targetAdmit == 0) {
            watermark.set(WorkloadPriority.ofLowest());
            log.info("[{}] grad:{} Updating watermark for admitMore, idle window admit all: {}/{}", name, gradient, admitted, requested);
            return;
        }

        int accAdmit = 0;
        final Iterator<Map.Entry<Integer, AtomicInteger>> lowerPriorities = lastWindow.histogram().tailMap(currentP, false).entrySet().iterator();
        if (!lowerPriorities.hasNext()) {
            watermark.set(WorkloadPriority.ofLowest());
            log.info("[{}] grad:{} Updating watermark for admitMore, beyond tail of histogram, admit all: {}/{}", name, gradient, admitted, requested);
            return;
        }

        int round = 0;
        while (lowerPriorities.hasNext()) {
            final Map.Entry<Integer, AtomicInteger> entry = lowerPriorities.next();
            final int candidateP = entry.getKey();
            final int candidateR = entry.getValue().get();
            accAdmit += candidateR;
            round++;
            if (accAdmit >= targetAdmit) { // TODO error rate
                watermark.updateAndGet(curr -> curr.deriveFrom(candidateP));
                log.info("[{}] grad:{}, round:{} Updating watermark for admitMore: {} -> {}, admit {}/{}", name, gradient, round, currentWatermark.simpleString(), watermark().simpleString(), accAdmit, targetAdmit);
                return;
            }

            if (!lowerPriorities.hasNext()) {
                log.info("[{}] grad:{}, round:{} histogram tail reached but still not enough for admit more: happy to admit all", name, gradient, round);
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
