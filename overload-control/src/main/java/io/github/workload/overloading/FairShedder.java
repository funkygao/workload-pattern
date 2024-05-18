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
    static final double GRADIENT_HEALTHY = 1d;
    static final double GRADIENT_IDLEST = HyperParameter.getDouble(Heuristic.GRADIENT_IDLEST, 1.2d);
    static final double GRADIENT_BUSIEST = HyperParameter.getDouble(Heuristic.GRADIENT_BUSIEST, 0.5d);
    static final double OVER_SHED_BOUND = HyperParameter.getDouble(Heuristic.OVER_SHED_BOUND, 1.01d);
    static final double DROP_RATE = HyperParameter.getDouble(Heuristic.SHED_DROP_RATE, 0.05d);
    static final double RECOVER_RATE = HyperParameter.getDouble(Heuristic.SHED_RECOVER_RATE, 0.015d);

    protected final String name;
    private final TumblingWindow<CountAndTimeWindowState> window;

    // 准入等级水位线/准入门槛，其优先级越高则准入控制越严格，即门槛越高.
    private final AtomicReference<WorkloadPriority> watermark = new AtomicReference<>(WorkloadPriority.ofLowest());

    /**
     * 计算过载梯度值：[{@link #GRADIENT_BUSIEST}, {@link #GRADIENT_IDLEST}].
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
        log.debug("[{}] predict with lastWindow workload({}/{}), grad:{}", name, lastWindow.admitted(), lastWindow.requested(), gradient);
        if (isOverloaded(gradient)) {
            penalizeFutureLowPriorities(lastWindow, gradient);
        } else {
            rewardFutureLowPriorities(lastWindow, gradient);
        }
    }

    protected final boolean isOverloaded(double gradient) {
        return gradient < GRADIENT_HEALTHY;
    }

    // 确保在精准提高 watermark 时不会因为过度抛弃低优先级请求而影响服务的整体可用性，尽可能保持高 goodput
    private void penalizeFutureLowPriorities(CountAndTimeWindowState lastWindow, double gradient) {
        final int admitted = lastWindow.admitted();
        final int targetDrop = (int) (DROP_RATE * admitted / gradient);
        final WorkloadPriority currentWatermark = watermark();
        if (targetDrop == 0) {
            log.debug("[{}] refuse raise bar for poor admit:{}, watermark {}, grad:{}", name, admitted, currentWatermark.simpleString(), gradient);
            return;
        }

        int accDrop = 0; // accumulated drop count
        final Iterator<Map.Entry<Integer, AtomicInteger>> higherPriorities = lastWindow.histogram().headMap(currentWatermark.P(), true).descendingMap().entrySet().iterator();
        if (!higherPriorities.hasNext()) {
            log.debug("[{}] refuse raise bar: already highest, watermark {}, grad:{}", name, currentWatermark.simpleString(), gradient);
            return;
        }

        int steps = 0; // 迈了几步
        while (higherPriorities.hasNext()) {
            final Map.Entry<Integer, AtomicInteger> entry = higherPriorities.next();
            final int candidateP = entry.getKey(); // WorkloadPriority#P
            final int candidateR = entry.getValue().get(); // 该P在上个周期被请求次数：包括shed量
            accDrop += candidateR;
            steps++;
            if (accDrop >= targetDrop) {
                double errorRate = (double) (accDrop - targetDrop) / targetDrop;
                int targetP;
                if (higherPriorities.hasNext() && errorRate < OVER_SHED_BOUND) {
                    // not overly shed and candidate is not head: shed candidate(inclusive) workload
                    targetP = higherPriorities.next().getKey();
                    steps++;
                } else {
                    targetP = candidateP; // this candidate wil not shed workload
                    accDrop -= candidateR; // 候选项并没有被shed，提前加上去的退回来
                    errorRate = (double) (accDrop - targetDrop) / targetDrop; // 重新计算误差率
                }

                watermark.updateAndGet(curr -> curr.deriveFromP(targetP));
                log.info("[{}] raise bar ok, steps:{}, {} -> {}, drop {}/{} err:{}, grad:{}", name, steps, currentWatermark.simpleString(), watermark().simpleString(), accDrop, targetDrop, errorRate, gradient);
                return;
            }

            if (!higherPriorities.hasNext()) { // read ahead
                watermark.updateAndGet(curr -> curr.deriveFromP(candidateP));
                log.info("[{}] raise bar stop early, steps:{}, {} -> {}, grad:{}", name, steps, currentWatermark.simpleString(), watermark().simpleString(), gradient);
                return;
            }
        }
    }

    private void rewardFutureLowPriorities(CountAndTimeWindowState lastWindow, double gradient) {
        final WorkloadPriority currentWatermark = watermark();
        if (currentWatermark.isLowest()) {
            return;
        }

        final int admitted = lastWindow.admitted();
        final int targetAdmit = (int) (RECOVER_RATE * gradient * admitted);
        if (targetAdmit == 0) {
            watermark.set(WorkloadPriority.ofLowest());
            log.info("[{}] lower bar to lowest, last window idle {}/{}, grad:{}", name, admitted, lastWindow.requested(), gradient);
            return;
        }

        int accAdmit = 0;
        final Iterator<Map.Entry<Integer, AtomicInteger>> lowerPriorities = lastWindow.histogram().tailMap(currentWatermark.P(), false).entrySet().iterator();
        if (!lowerPriorities.hasNext()) {
            watermark.set(WorkloadPriority.ofLowest());
            log.info("[{}] lower bar to lowest, has no lower peer, grad:{}", name, gradient);
            return;
        }

        int steps = 0; // 迈了几步
        while (lowerPriorities.hasNext()) {
            final Map.Entry<Integer, AtomicInteger> entry = lowerPriorities.next();
            final int candidateP = entry.getKey();
            final int candidateR = entry.getValue().get();
            accAdmit += candidateR;
            steps++;
            if (accAdmit >= targetAdmit) {
                watermark.updateAndGet(curr -> curr.deriveFromP(candidateP));
                final double errorRate = (double) (accAdmit - targetAdmit) / targetAdmit;
                log.info("[{}] lower bar ok, steps:{}, {} -> {}, admit {}/{} err:{}, grad:{}", name, steps, currentWatermark.simpleString(), watermark().simpleString(), accAdmit, targetAdmit, errorRate, gradient);
                return;
            }

            if (!lowerPriorities.hasNext()) { // read ahead
                log.info("[{}] lower bar to lowest, stop early, steps:{}, grad:{}", name, gradient, steps);
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
        // 在水位线(含)以下的请求都放行
        return priority.P() <= watermark().P();
    }

    @VisibleForTesting
    void resetForTesting() {
        this.window.resetForTesting();
        this.watermark.set(WorkloadPriority.ofLowest());
        log.debug("[{}] has been reset for testing purposes.", name);
    }
}
