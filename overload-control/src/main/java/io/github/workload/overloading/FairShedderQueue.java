package io.github.workload.overloading;

import io.github.workload.HyperParameter;
import io.github.workload.annotations.VisibleForTesting;
import io.github.workload.metrics.tumbling.CountAndTimeWindowState;
import io.github.workload.metrics.tumbling.WindowConfig;
import lombok.Generated;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ThreadLocalRandom;

@Slf4j
class FairShedderQueue extends FairShedder {
    static final long AVG_QUEUED_MS_UPPER_BOUND = HyperParameter.getLong(Empirical.AVG_QUEUED_MS_UPPER_BOUND, 20);

    private volatile long lastOverloadNs = 0;
    private final long timeCycleNs;

    FairShedderQueue(String name) {
        super(name);
        this.timeCycleNs = windowConfig().getTimeCycleNs();
        log.info("[{}] created with timeCycle:{}ms, AVG_QUEUED_MS_UPPER_BOUND:{}", name, timeCycleNs / WindowConfig.NS_PER_MS, AVG_QUEUED_MS_UPPER_BOUND);
    }

    @Override
    protected double overloadGradient(long nowNs, CountAndTimeWindowState snapshot) {
        boolean stillExplicitOverloaded = nowNs > 0 && lastOverloadNs > 0
                && (nowNs - lastOverloadNs) <= timeCycleNs;
        if (stillExplicitOverloaded) {
            double grad = explicitOverloadGradient();
            log.warn("[{}] within explicit overload period:{}ms, rand grad:{}", name, timeCycleNs / WindowConfig.NS_PER_MS, grad);
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
        double rawGradient = upperBound / avgQueuedMs;
        double grad = Math.min(GRADIENT_IDLEST, Math.max(GRADIENT_BUSIEST, rawGradient));
        if (isOverloaded(grad)) {
            log.warn("[{}] buffer bloat, avg:{} > {}, grad:{}", name, avgQueuedMs, upperBound, grad);
        } else {
            log.debug("[{}] avg queuing ms:{} < {}", name, avgQueuedMs, upperBound);
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
    void resetForTesting() {
        super.resetForTesting();
        lastOverloadNs = 0;
    }

}
