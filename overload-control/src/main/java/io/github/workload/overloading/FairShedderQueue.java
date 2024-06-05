package io.github.workload.overloading;

import io.github.workload.HyperParameter;
import io.github.workload.annotations.VisibleForTesting;
import io.github.workload.metrics.tumbling.CountAndTimeWindowState;
import io.github.workload.metrics.tumbling.WindowConfig;
import lombok.Generated;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
class FairShedderQueue extends FairShedder {
    static final long AVG_QUEUED_MS_UPPER_BOUND = HyperParameter.getLong(Empirical.AVG_QUEUED_MS_UPPER_BOUND, 50);

    private volatile long lastOverloadNs = 0;
    private final AtomicLong lastOverloadTtlNs;

    FairShedderQueue(String name) {
        super(name);
        this.lastOverloadTtlNs = windowConfig().getTimeCycleNs();
        log.info("[{}] created with AVG_QUEUED_MS_UPPER_BOUND:{}, explicit overload signal ttl:{}ms", name, AVG_QUEUED_MS_UPPER_BOUND, lastOverloadTtlNs.get() / WindowConfig.NS_PER_MS);
    }

    @Override
    protected double overloadGradient(long nowNs, CountAndTimeWindowState snapshot) {
        final long ttlNs = lastOverloadTtlNs.get();
        final boolean stillExplicitOverloaded = nowNs > 0 && lastOverloadNs > 0
                && (nowNs - lastOverloadNs) <= ttlNs;
        if (stillExplicitOverloaded) {
            double grad = explicitOverloadGradient();
            log.warn("[{}] within explicit overload ttl:{}ms, rand grad:{}", name, ttlNs / WindowConfig.NS_PER_MS, grad);
            return grad;
        }

        return queuingGradient(snapshot.avgQueuedMs(), AVG_QUEUED_MS_UPPER_BOUND);
    }

    // 显式过载的梯度值
    @VisibleForTesting
    double explicitOverloadGradient() {
        // TODO
        return GRADIENT_BUSIEST + ThreadLocalRandom.current().nextDouble(GRADIENT_HEALTHY - GRADIENT_BUSIEST);
    }

    @VisibleForTesting
    double queuingGradient(double avgQueuedMs, double upperBound) {
        final double rawGradient = upperBound / avgQueuedMs;
        final double grad = Math.min(GRADIENT_IDLEST, Math.max(GRADIENT_BUSIEST, rawGradient));
        if (isOverloaded(grad)) {
            log.warn("[{}] buffer bloat, avg:{} > {}, grad:{}", name, avgQueuedMs, upperBound, grad);
        }
        return grad;
    }

    void addWaitingNs(long waitingNs) {
        currentWindow().waitNs(waitingNs);
    }

    void overload(long overloadedAtNs) {
        log.debug("[{}] got explicit overload feedback", name);
        this.lastOverloadNs = overloadedAtNs;
    }

    @VisibleForTesting
    @Generated
    @Override
    synchronized void resetForTesting() {
        super.resetForTesting();
        lastOverloadNs = 0;
    }

}
