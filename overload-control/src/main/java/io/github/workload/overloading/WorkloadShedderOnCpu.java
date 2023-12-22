package io.github.workload.overloading;

import io.github.workload.SystemClock;
import io.github.workload.SystemLoadProvider;
import io.github.workload.annotations.VisibleForTesting;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class WorkloadShedderOnCpu extends WorkloadShedder {
    private static final SystemClock coolOffClock = SystemClock.ofPrecisionMs(1000);
    private final double cpuUsageUpperBound;
    private final long coolOffMs;
    private final long startupMs;

    @VisibleForTesting
    SystemLoadProvider loadProvider = SystemLoad.getInstance();

    WorkloadShedderOnCpu(double cpuUsageUpperBound, long coolOffSec) {
        super("CPU");
        this.cpuUsageUpperBound = cpuUsageUpperBound;
        this.coolOffMs = coolOffSec * 1000;
        this.startupMs = coolOffClock.currentTimeMillis();
        log.info("[{}] created with upper bound:{}, cool off:{}sec", this.name, cpuUsageUpperBound, coolOffSec);
    }

    @Override
    protected boolean isOverloaded(long nowNs) {
        if (coolOffMs > 0 && coolOffClock.currentTimeMillis() - startupMs < coolOffMs) {
            // 有静默期，而且在静默期内，永远认为不过载
            return false;
        }

        double cpuUsage = loadProvider.cpuUsage();
        boolean overloaded = cpuUsage > cpuUsageUpperBound;
        if (overloaded) {
            log.warn("CPU BUSY, usage: {} > {}", cpuUsage, cpuUsageUpperBound);
        }
        return overloaded;
    }

}
