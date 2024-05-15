package io.github.workload.overloading;

import io.github.workload.HyperParameter;
import io.github.workload.Sysload;
import io.github.workload.annotations.VisibleForTesting;
import io.github.workload.metrics.smoother.ValueSmoother;
import io.github.workload.metrics.tumbling.CountAndTimeWindowState;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * 基于EMA的CPU负载
 *
 * <p>高吞吐系统下的CPU波动可能剧烈而产生毛刺(phenomenon burr)，为此通过EMA使得其平滑.</p>
 */
@Slf4j
class FairShedderCpu extends FairShedder {
    private static final double CPU_EMA_ALPHA = HyperParameter.getDouble(Heuristic.CPU_EMA_ALPHA, 0.25d);

    private final double cpuUsageUpperBound;
    private Sysload sysload;

    @VisibleForTesting
    final ValueSmoother valueSmoother;

    FairShedderCpu(double cpuUsageUpperBound, long coolOffSec) {
        this(cpuUsageUpperBound, new ContainerLoad(coolOffSec));
    }

    FairShedderCpu(double cpuUsageUpperBound, @NonNull Sysload sysload) {
        super("CPU");
        this.cpuUsageUpperBound = cpuUsageUpperBound;
        this.sysload = sysload;
        this.valueSmoother = ValueSmoother.ofEMA(CPU_EMA_ALPHA);
        log.info("[{}] created with sysload:{}, upper bound:{}, ema alpha:{}", this.name, sysload.getClass().getSimpleName(), cpuUsageUpperBound, CPU_EMA_ALPHA);
    }

    @Override
    protected double overloadGradient(long nowNs, CountAndTimeWindowState snapshot) {
        final double cpuUsage = smoothedCpuUsage();
        final double gradient = gradient(cpuUsage, cpuUsageUpperBound);
        if (isOverloaded(gradient)) {
            log.warn("CPU BUSY:{} > {}", cpuUsage, cpuUsageUpperBound);
        }
        return gradient;
    }

    @VisibleForTesting
    double gradient(double cpuUsage, double upperBound) {
        double rawGradient = upperBound / cpuUsage;
        return Math.min(GRADIENT_IDLE, Math.max(GRADIENT_BUSIEST, rawGradient));
    }

    @VisibleForTesting
    void setSysload(Sysload sysload) {
        if (this.sysload instanceof ContainerLoad) {
            ContainerLoad.stop();
        }

        log.info("sysload: {} -> {}", this.sysload.getClass().getSimpleName(), sysload.getClass().getSimpleName());
        this.sysload = sysload;
    }

    private double smoothedCpuUsage() {
        double cpuUsage = sysload.cpuUsage();
        if (cpuUsage < 0) {
            cpuUsage = 0.0d;
        }
        if (cpuUsage > 1) {
            cpuUsage = 1.0d;
        }
        double smoothed = valueSmoother.update(cpuUsage).smoothedValue();
        log.debug("cpu usage:{}, smoothed:{}", cpuUsage, smoothed);
        return smoothed;
    }
}