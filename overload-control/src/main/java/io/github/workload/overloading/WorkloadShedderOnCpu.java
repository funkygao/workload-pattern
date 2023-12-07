package io.github.workload.overloading;

import io.github.workload.SystemLoad;
import io.github.workload.annotations.NotThreadSafe;
import lombok.extern.slf4j.Slf4j;

@NotThreadSafe
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
            log.warn("CPU BUSY, cpu usage: {}%", cpuUsage * 100);
        }
        return overloaded;
    }
}
