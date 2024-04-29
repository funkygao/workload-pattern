package io.github.workload.overloading;

import io.github.workload.SystemClock;
import io.github.workload.SystemLoadProvider;
import io.github.workload.annotations.Heuristics;
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
    private static final SystemClock coolOffClock = SystemClock.ofPrecisionMs(1000);

    @Heuristics
    static final double EMA_ALPHA = 0.25d;
    @Heuristics("局限性：low false positive rate is not guaranteed")
    private final double cpuUsageUpperBound;
    @Heuristics
    private final long coolOffMs;

    @VisibleForTesting
    final ValueSmoother valueSmoother;

    @VisibleForTesting
    SystemLoadProvider loadProvider;

    WorkloadShedderOnCpu(double cpuUsageUpperBound, long coolOffSec) {
        super("CPU");
        this.cpuUsageUpperBound = cpuUsageUpperBound;
        this.coolOffMs = coolOffSec * 1000;
        this.loadProvider = SystemLoad.getInstance(coolOffSec);
        this.valueSmoother = ValueSmoother.ofEMA(EMA_ALPHA);
        log.info("[{}] created with upper bound:{}, cool off:{}sec", this.name, cpuUsageUpperBound, coolOffSec);
    }

    @Override
    protected boolean isOverloaded(long nowNs, CountAndTimeWindowState windowState) {
        if (coolOffMs > 0 && coolOffClock.currentTimeMillis() - startupMs < coolOffMs) {
            // 有静默期，而且在静默期内，永远认为不过载
            return false;
        }

        double cpuUsage = smoothedCpuUsage();
        boolean overloaded = cpuUsage > cpuUsageUpperBound;
        if (overloaded) {
            log.warn("CPU BUSY with utilization:{} > {}", cpuUsage, cpuUsageUpperBound);
        }
        return overloaded;
    }

    // use smoother to mitigate false positive
    private double smoothedCpuUsage() {
        double cpuUsage = loadProvider.cpuUsage();
        return valueSmoother.update(cpuUsage).smoothedValue();
    }

}
