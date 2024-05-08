package io.github.workload.overloading;

import io.github.workload.HyperParameter;
import io.github.workload.SystemLoadProvider;
import io.github.workload.annotations.VisibleForTesting;
import io.github.workload.metrics.smoother.ValueSmoother;
import io.github.workload.metrics.tumbling.CountAndTimeWindowState;
import lombok.extern.slf4j.Slf4j;

/**
 * 基于EMA的CPU负载
 *
 * <p>高吞吐系统下的CPU波动可能剧烈而产生毛刺(phenomenon burr)，为此通过EMA使得其平滑.</p>
 */
@Slf4j
class WorkloadShedderOnCpu extends WorkloadShedder {
    private static final double CPU_EMA_ALPHA = HyperParameter.getDouble(JVM.CPU_EMA_ALPHA, 0.25d);

    private final double cpuUsageUpperBound;

    @VisibleForTesting
    final ValueSmoother valueSmoother;

    @VisibleForTesting("not final, so that test can mock")
    SystemLoadProvider loadProvider;

    WorkloadShedderOnCpu(double cpuUsageUpperBound, long coolOffSec) {
        super("CPU");
        this.cpuUsageUpperBound = cpuUsageUpperBound;
        this.loadProvider = SystemLoad.getInstance(coolOffSec); // 过了静默期才采样CPU
        this.valueSmoother = ValueSmoother.ofEMA(CPU_EMA_ALPHA);
        log.info("[{}] created with upper bound:{}, cool off:{}sec, ema alpha:{}", this.name, cpuUsageUpperBound, coolOffSec, CPU_EMA_ALPHA);
    }

    @Override
    protected boolean isOverloaded(long nowNs, CountAndTimeWindowState windowState) {
        final double cpuUsage = smoothedCpuUsage();
        final boolean overloaded = cpuUsage > cpuUsageUpperBound;
        if (overloaded) {
            log.warn("CPU BUSY with utilization:{} > {}", cpuUsage, cpuUsageUpperBound);
        }
        return overloaded;
    }

    private double smoothedCpuUsage() {
        double cpuUsage = loadProvider.cpuUsage();
        return valueSmoother.update(cpuUsage).smoothedValue();
    }

}
