package io.github.workload.overloading;

import io.github.workload.HyperParameter;
import io.github.workload.WorkloadPriority;
import io.github.workload.annotations.ThreadSafe;
import io.github.workload.annotations.VisibleForTesting;
import io.github.workload.aqm.PIDController;
import io.github.workload.metrics.tumbling.CountAndTimeRolloverStrategy;
import io.github.workload.metrics.tumbling.CountAndTimeWindowState;
import io.github.workload.metrics.tumbling.TumblingWindow;
import io.github.workload.metrics.tumbling.WindowConfig;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
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
    protected final double GRADIENT_HEALTHY = 1d;
    static final double GRADIENT_IDLEST = HyperParameter.getDouble(Empirical.GRADIENT_IDLEST, 1.2d);
    static final double GRADIENT_BUSIEST = HyperParameter.getDouble(Empirical.GRADIENT_BUSIEST, 0.5d);
    static final double OVER_SHED_BOUND = HyperParameter.getDouble(Empirical.OVER_SHED_BOUND, 1.01d);
    static final double DROP_RATE = HyperParameter.getDouble(Empirical.SHED_DROP_RATE, 0.05d);
    static final double RECOVER_RATE = HyperParameter.getDouble(Empirical.SHED_RECOVER_RATE, 0.03d);

    protected final String name;
    private final TumblingWindow<CountAndTimeWindowState> window;

    // 准入等级水位线/准入门槛，其优先级越高则准入控制越严格，即门槛越高.
    private final AtomicReference<WorkloadPriority> watermark = new AtomicReference<>(WorkloadPriority.ofLowest());

    private final PIDController pidController;

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
        this.pidController = new PIDController(0.1, 0.01, 0.05, 1);
    }

    boolean admit(@NonNull WorkloadPriority priority) {
        boolean admitted = satisfyWatermark(priority);
        if (!admitted) {
            admitted = stochasticAdmit(priority);
        }
        window.advance(priority, admitted, System.nanoTime());
        return admitted;
    }

    WorkloadPriority watermark() {
        return watermark.get();
    }

    @VisibleForTesting
    void predictWatermark(CountAndTimeWindowState lastWindow, double gradient) {
        // 引入反馈机制：在每个窗口结束时，根据实际的 shed 情况和系统性能指标，对预测模型进行反馈和调整
        // 动态调整窗口大小：根据系统的负载情况动态调整窗口的大小。当系统负载较高时，缩小窗口大小，以更快地响应负载变化
        log.trace("[{}] predict with lastWindow workload({}/{}), grad:{}", name, lastWindow.admitted(), lastWindow.requested(), gradient);
        if (isOverloaded(gradient)) {
            penalizeFutureLowPriorities(lastWindow, gradient);
        } else {
            rewardFutureLowPriorities(lastWindow, gradient);
        }
    }

    // 确保在精准提高 watermark 时不会因为过度抛弃低优先级请求而影响服务的整体可用性，尽可能保持高 goodput
    private void penalizeFutureLowPriorities(CountAndTimeWindowState lastWindow, double gradient) {
        final int requested = lastWindow.requested();
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
            log.debug("[{}] refuse raise bar for being highest, watermark {}, grad:{}", name, currentWatermark.simpleString(), gradient);
            return;
        }

        int steps = 0; // 迈了几步
        while (true) {
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
                log.warn("[{}] raise bar ok, last drop:{}/{}, steps:{}, {} -> {}, to drop {}/{} err:{}, grad:{}", name, lastWindow.shedded(), requested, steps, currentWatermark.simpleString(), watermark().simpleString(), accDrop, targetDrop, errorRate, gradient);
                return;
            }

            if (!higherPriorities.hasNext()) {
                // 凑不够数了：best effort
                watermark.updateAndGet(curr -> curr.deriveFromP(candidateP));
                log.warn("[{}] raise bar stop early, last drop:{}/{}, steps:{}, {} -> {}, to drop {}/{}, grad:{}", name, lastWindow.shedded(), requested, steps, currentWatermark.simpleString(), watermark().simpleString(), accDrop, targetDrop, gradient);
                return;
            }
        }
    }

    private void rewardFutureLowPriorities(CountAndTimeWindowState lastWindow, double gradient) {
        final WorkloadPriority currentWatermark = watermark();
        if (currentWatermark.isLowest()) {
            return;
        }

        final int requested = lastWindow.requested();
        final int admitted = lastWindow.admitted();
        boolean degraded = false;
        int targetAdmit = (int) (RECOVER_RATE * gradient * admitted);
        if (targetAdmit == 0) {
            // 1000个请求，admit 10，则目标：30
            targetAdmit = (int) (RECOVER_RATE * gradient * requested);
            degraded = true;
        }
        if (targetAdmit == 0) {
            watermark.set(WorkloadPriority.ofLowest());
            log.info("[{}] lower bar to 0 for idle window, last drop:{}/{}, grad:{}", name, lastWindow.shedded(), requested, gradient);
            return;
        }

        int accAdmit = 0;
        final Iterator<Map.Entry<Integer, AtomicInteger>> lowerPriorities = lastWindow.histogram().tailMap(currentWatermark.P(), false).entrySet().iterator();
        if (!lowerPriorities.hasNext()) {
            watermark.set(WorkloadPriority.ofLowest());
            log.info("[{}] lower bar to 0 for being last stop, last drop:{}/{}, grad:{}", name, lastWindow.shedded(), requested, gradient);
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
                if (degraded) {
                    log.warn("[{}] lower bar degraded, last drop:{}/{}, steps:{}, {} -> {}, to admit {}/{} err:{}, grad:{}", name, lastWindow.shedded(), requested, steps, currentWatermark.simpleString(), watermark().simpleString(), accAdmit, targetAdmit, errorRate, gradient);
                } else {
                    log.warn("[{}] lower bar ok, last drop:{}/{}, steps:{}, {} -> {}, to admit {}/{} err:{}, grad:{}", name, lastWindow.shedded(), requested, steps, currentWatermark.simpleString(), watermark().simpleString(), accAdmit, targetAdmit, errorRate, gradient);
                }
                return;
            }
        }

        // 凑不够数了
        log.warn("[{}] lower bar to 0, stop early, last drop:{}/{}, steps:{}, grad:{}", name, lastWindow.shedded(), requested, steps, gradient);
        watermark.set(WorkloadPriority.ofLowest());
    }

    protected final CountAndTimeWindowState currentWindow() {
        return window.current();
    }

    protected final WindowConfig<CountAndTimeWindowState> windowConfig() {
        return window.getConfig();
    }

    protected final boolean isOverloaded(double gradient) {
        return gradient < GRADIENT_HEALTHY;
    }

    private boolean satisfyWatermark(WorkloadPriority priority) {
        // 在水位线(含)以下的请求都放行
        return priority.P() <= watermark().P();
    }

    // 概率拒绝：一种简单且有效解决请求优先级分布不均的方法
    // 对于低优先级请求，不是完全拒绝，而是按一定的概率拒绝
    // 这种方法可以确保每个优先级级别的请求都能得到一定处理，同时又可以限制低优先级请求对系统的影响
    private boolean stochasticAdmit(WorkloadPriority priority) {
        int P = priority.P();
        if (false) {
            Map<Integer, Integer> weights = new HashMap<>();
            Map<Integer, Double> rejectProbabilities = new HashMap<>();
            final int weight = weights.get(P);
            final double rejectProbability = rejectProbabilities.get(P);
            final double probability = weight / (weight + rejectProbability);
            return ThreadLocalRandom.current().nextDouble() < probability;
        }
        return false;
    }

    @VisibleForTesting
    synchronized void resetForTesting() {
        this.window.resetForTesting();
        this.watermark.set(WorkloadPriority.ofLowest());
        log.debug("[{}] has been reset for testing purposes.", name);
    }
}
