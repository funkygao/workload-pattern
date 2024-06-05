package io.github.workload.overloading;

import io.github.workload.HyperParameter;
import io.github.workload.WorkloadPriority;
import io.github.workload.annotations.ThreadSafe;
import io.github.workload.annotations.VisibleForTesting;
import io.github.workload.metrics.tumbling.CountAndTimeRolloverStrategy;
import io.github.workload.metrics.tumbling.CountAndTimeWindowState;
import io.github.workload.metrics.tumbling.TumblingWindow;
import io.github.workload.metrics.tumbling.WindowConfig;
import io.github.workload.control.PIDController;
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
    // PID控制
    private static final boolean PID_CONTROL = false;
    private static final int PID_TARGET_IGNORED = 0;

    // 梯度
    protected final double GRADIENT_HEALTHY = 1d; // will never change
    static final double GRADIENT_IDLEST = HyperParameter.getDouble(Empirical.GRADIENT_IDLEST, 1.5d);
    static final double GRADIENT_BUSIEST = HyperParameter.getDouble(Empirical.GRADIENT_BUSIEST, 0.5d);

    // 经验值
    static final double OVER_SHED_BOUND = HyperParameter.getDouble(Empirical.OVER_SHED_BOUND, 1.01d);
    static final double DROP_RATE_BASE = HyperParameter.getDouble(Empirical.SHED_DROP_RATE, 0.05d);
    static final double RECOVER_RATE_BASE = HyperParameter.getDouble(Empirical.SHED_RECOVER_RATE, 0.03d);

    protected final String name;
    private final TumblingWindow<CountAndTimeWindowState> window;

    // 准入等级水位线/准入门槛，其优先级越高则准入控制越严格，即门槛越高.
    private final AtomicReference<WorkloadPriority> watermark = new AtomicReference<>(WorkloadPriority.ofLowest());

    private final ShedStochastic stochastic;
    private final WatermarkHistory history = new WatermarkHistory(20);
    private final PIDController pidController;
    private final AtomicInteger lastTargetCount = new AtomicInteger(PID_TARGET_IGNORED);

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
        this(name, ShedStochastic.newDefault());
    }

    protected FairShedder(String name, ShedStochastic shedStochastic) {
        this.name = name;
        WindowConfig<CountAndTimeWindowState> config = WindowConfig.create(
                new CountAndTimeRolloverStrategy() {
                    @Override
                    public void onRollover(long nowNs, CountAndTimeWindowState snapshot, TumblingWindow<CountAndTimeWindowState> window) {
                        predictWatermark(snapshot, overloadGradient(nowNs, snapshot), nowNs);
                    }
                }
        );
        this.window = new TumblingWindow<>(config, name, System.nanoTime());
        this.pidController = new PIDController(0.1, 0.01, 0.05);
        this.stochastic = shedStochastic;
    }

    boolean admit(@NonNull WorkloadPriority priority) {
        boolean admitted = satisfyWatermark(priority);
        if (!admitted && stochastic != null) {
            admitted = !stochastic.shouldShed(priority);
        }
        window.advance(priority, admitted, System.nanoTime());
        return admitted;
    }

    WorkloadPriority watermark() {
        return watermark.get();
    }

    void predictWatermark(CountAndTimeWindowState lastWindow, double gradient, long nowNs) {
        final double shedRatio = lastWindow.shedRatio();
        history.addHistory(shedRatio, watermark());
        if (log.isTraceEnabled()) {
            log.trace("[{}] predict with lastWindow workload admitted({}/{}), grad:{}, shedRatio:{}", name, lastWindow.admitted(), lastWindow.requested(), gradient, shedRatio);
        }

        final boolean overloaded = isOverloaded(gradient);
        if (overloaded) {
            penalizeFutureLowPriorities(lastWindow, gradient);
        } else {
            rewardFutureLowPriorities(lastWindow, gradient);
        }

        pidControlWatermark(lastWindow, nowNs, overloaded);

        // 根据系统的负载情况动态调整窗口的大小
        window.zoomTimeCycle(1);
    }

    // 类ReLU的激活函数
    double relu(double value, double threshold) {
        return Math.max(0.1, (1000 - value) / (1000 - threshold));
    }

    // 确保在精准提高 watermark 时不会因为过度抛弃低优先级请求而影响服务的整体可用性，尽可能保持高 goodput
    private void penalizeFutureLowPriorities(CountAndTimeWindowState lastWindow, double gradient) {
        final int requested = lastWindow.requested();
        final int admitted = lastWindow.admitted();
        final double actualDropRate = DROP_RATE_BASE / gradient;
        final int targetDrop = (int) (actualDropRate * admitted);
        final WorkloadPriority currentWatermark = watermark();
        if (targetDrop == 0) {
            log.debug("[{}] refuse raise bar for poor admit:{}, watermark:{}, grad:{}", name, admitted, currentWatermark.simpleString(), gradient);
            ignorePIDControl();
            return;
        }

        int accDrop = 0; // accumulated drop count
        final Iterator<Map.Entry<Integer, AtomicInteger>> higherPriorities = lastWindow.histogram().headMap(currentWatermark.P(), true).descendingMap().entrySet().iterator();
        if (!higherPriorities.hasNext()) {
            // should never happen
            log.error("[{}] refuse raise bar for being highest, watermark:{}, grad:{}", name, currentWatermark.simpleString(), gradient);
            ignorePIDControl();
            return;
        }

        lastTargetCount.set(targetDrop);
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
                    // TODO 线性插值
                    targetP = candidateP; // this candidate wil not shed workload
                    accDrop -= candidateR; // 候选项并没有被shed，提前加上去的退回来
                    errorRate = (double) (accDrop - targetDrop) / targetDrop; // 重新计算误差率
                }

                watermark.updateAndGet(curr -> curr.deriveFromP(targetP));
                log.warn("[{}] raise bar ok: {} -> {}, last drop:{}/{}, steps:{}, to drop {}/{} err:{}, grad:{}", name, currentWatermark.simpleString(), watermark().simpleString(), lastWindow.shedded(), requested, steps, accDrop, targetDrop, errorRate, gradient);
                return;
            }

            if (!higherPriorities.hasNext()) {
                // 凑不够数了：best effort
                watermark.updateAndGet(curr -> curr.deriveFromP(candidateP));
                log.warn("[{}] raise bar stop early: {} -> {}, last drop:{}/{}, steps:{}, to drop {}/{}, grad:{}", name, currentWatermark.simpleString(), watermark().simpleString(), lastWindow.shedded(), requested, steps, accDrop, targetDrop, gradient);
                return;
            }
        }
    }

    private void rewardFutureLowPriorities(CountAndTimeWindowState lastWindow, double gradient) {
        final WorkloadPriority currentWatermark = watermark();
        if (currentWatermark.isLowest()) {
            ignorePIDControl();
            return;
        }

        final int requested = lastWindow.requested();
        final int admitted = lastWindow.admitted();
        boolean degraded = false;
        final double actualRecoverRate = RECOVER_RATE_BASE * gradient;
        int targetAdmit = (int) (actualRecoverRate * admitted);
        if (targetAdmit == 0) {
            // 1000个请求，admit 10，则目标：30
            targetAdmit = (int) (RECOVER_RATE_BASE * gradient * requested);
            degraded = true;
        }
        if (targetAdmit == 0) {
            watermark.set(WorkloadPriority.ofLowest());
            ignorePIDControl();
            log.warn("[{}] lower bar for idle window: {} -> {}, last drop:{}/{}, grad:{}", name, currentWatermark.simpleString(), watermark().simpleString(), lastWindow.shedded(), requested, gradient);
            return;
        }

        int accAdmit = 0;
        final Iterator<Map.Entry<Integer, AtomicInteger>> lowerPriorities = lastWindow.histogram().tailMap(currentWatermark.P(), false).entrySet().iterator();
        if (!lowerPriorities.hasNext()) {
            watermark.set(WorkloadPriority.ofLowest());
            ignorePIDControl();
            log.warn("[{}] lower bar for being last stop: {} -> {}, last drop:{}/{}, grad:{}", name, currentWatermark.simpleString(), watermark().simpleString(), lastWindow.shedded(), requested, gradient);
            return;
        }

        lastTargetCount.set(targetAdmit);
        int steps = 0;
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
                    log.warn("[{}] lower bar degraded: {} -> {}, last drop:{}/{}, steps:{}, to admit {}/{} err:{}, grad:{}", name, currentWatermark.simpleString(), watermark().simpleString(), lastWindow.shedded(), requested, steps, accAdmit, targetAdmit, errorRate, gradient);
                } else {
                    log.warn("[{}] lower bar ok: {} -> {}, last drop:{}/{}, steps:{}, to admit {}/{} err:{}, grad:{}", name, currentWatermark.simpleString(), watermark().simpleString(), lastWindow.shedded(), requested, steps, accAdmit, targetAdmit, errorRate, gradient);
                }
                return;
            }
        }

        // 凑不够数了
        watermark.set(WorkloadPriority.ofLowest());
        log.warn("[{}] lower bar stop early: {} -> {}, last drop:{}/{}, steps:{}, grad:{}", name, currentWatermark.simpleString(), watermark().simpleString(), lastWindow.shedded(), requested, steps, gradient);
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

    private void ignorePIDControl() {
        lastTargetCount.set(PID_TARGET_IGNORED);
    }

    private void pidControlWatermark(CountAndTimeWindowState lastWindow, long nowNs, boolean overloaded) {
        final int target = lastTargetCount.get();
        if (target == PID_TARGET_IGNORED) {
            return;
        }

        int actual;
        if (overloaded) {
            actual = lastWindow.shedded();
        } else {
            actual = lastWindow.admitted();
        }
        final double err = actual - target; // 偏差
        final double pidOutput = pidController.getOutput(err, nowNs);
        log.trace("[{}] PID({})={}, actual:{}, target:{}", name, err, pidOutput, actual, target);

        if (PID_CONTROL) {
            final WorkloadPriority current = watermark();
            int P = current.P() + (int) pidOutput; // TODO
            P = Math.min(0, Math.max(WorkloadPriority.ofLowest().P(), P));
            final WorkloadPriority newWatermark = WorkloadPriority.fromP(P);
            watermark.set(newWatermark);
            log.info("[{}] watermark by PID, {} -> {}", name, current.simpleString(), newWatermark.simpleString());
        }
    }

    @VisibleForTesting
    synchronized void resetForTesting() {
        this.window.resetForTesting();
        this.watermark.set(WorkloadPriority.ofLowest());
        this.lastTargetCount.set(0);
        log.debug("[{}] has been reset for testing purpose", name);
    }
}
