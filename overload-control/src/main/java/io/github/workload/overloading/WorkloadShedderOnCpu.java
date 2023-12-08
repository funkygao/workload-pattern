package io.github.workload.overloading;

import io.github.workload.SystemLoad;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class WorkloadShedderOnCpu extends WorkloadShedder {
    private final double cpuUsageUpperBound;

    WorkloadShedderOnCpu(double cpuUsageUpperBound) {
        super();
        this.cpuUsageUpperBound = cpuUsageUpperBound;
    }

    @Override
    protected boolean isOverloaded(long nowNs) {
        double cpuUsage = SystemLoad.cpuUsage();
        boolean overloaded = cpuUsage > cpuUsageUpperBound;
        if (overloaded) {
            log.warn("CPU BUSY, usage: {} > {}", cpuUsage, cpuUsageUpperBound);
        }
        return overloaded;
    }
}
