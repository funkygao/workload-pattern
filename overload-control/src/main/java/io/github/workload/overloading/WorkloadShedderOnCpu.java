package io.github.workload.overloading;

import io.github.workload.SystemLoadProvider;
import io.github.workload.annotations.VisibleForTesting;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class WorkloadShedderOnCpu extends WorkloadShedder {
    private final double cpuUsageUpperBound;

    @VisibleForTesting
    SystemLoadProvider loadProvider = SystemLoad.getInstance();

    WorkloadShedderOnCpu(double cpuUsageUpperBound) {
        super("CPU");
        this.cpuUsageUpperBound = cpuUsageUpperBound;
        log.info("created with upper bound:{}", cpuUsageUpperBound);
    }

    @Override
    protected boolean isOverloaded(long nowNs) {
        double cpuUsage = loadProvider.cpuUsage();
        boolean overloaded = cpuUsage > cpuUsageUpperBound;
        if (overloaded) {
            log.warn("CPU BUSY, usage: {} > {}", cpuUsage, cpuUsageUpperBound);
        }
        return overloaded;
    }

}
