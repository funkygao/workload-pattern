package io.github.workload.overloading;

import io.github.workload.HyperParameter;
import io.github.workload.annotations.VisibleForTesting;
import io.github.workload.metrics.tumbling.CountAndTimeWindowState;
import lombok.Generated;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ThreadLocalRandom;

@Slf4j
class WorkloadShedderOnQueue extends WorkloadShedder {
    @VisibleForTesting
    static final long AVG_QUEUED_MS_UPPER_BOUND = HyperParameter.getLong(Heuristic.AVG_QUEUED_MS_UPPER_BOUND, 200);

    private volatile long overloadedAtNs = 0; // 最近一次显式过载的时间
    private final long timeCycleNs;

    WorkloadShedderOnQueue(String name) {
        super(name);
        this.timeCycleNs = windowConfig().getTimeCycleNs();
    }

    @Override
    protected double overloadGradient(long nowNs, CountAndTimeWindowState snapshot) {
        boolean stillExplicitOverloaded = nowNs > 0 && overloadedAtNs > 0
                && (nowNs - overloadedAtNs) <= timeCycleNs;
        if (stillExplicitOverloaded) {
            log.debug("[{}] still in explicit overload interval, timeCycleNs:{}", name, timeCycleNs);
            return explicitOverloadGradient();
        }

        // bufferbloat
        return queuingGradient(snapshot.avgQueuedMs(), AVG_QUEUED_MS_UPPER_BOUND);
    }

    // 显式过载的梯度值
    @VisibleForTesting
    double explicitOverloadGradient() {
        return GRADIENT_BUSIEST + ThreadLocalRandom.current().nextDouble(GRADIENT_BUSIEST);
    }

    @VisibleForTesting
    double queuingGradient(double avgQueuedMs, double upperBound) {
        double rawGradient = upperBound / avgQueuedMs;
        return Math.min(GRADIENT_IDLE, Math.max(GRADIENT_BUSIEST, rawGradient));
    }

    void addWaitingNs(long waitingNs) {
        currentWindow().waitNs(waitingNs);
    }

    void overload(long overloadedAtNs) {
        log.debug("[{}] got explicit overload feedback", name);
        this.overloadedAtNs = overloadedAtNs;
    }

    @VisibleForTesting
    @Generated
    @Override
    void resetForTesting() {
        super.resetForTesting();
        overloadedAtNs = 0;
    }

}
