package io.github.workload.overloading;

import io.github.workload.annotations.Heuristics;
import io.github.workload.annotations.VisibleForTesting;
import io.github.workload.metrics.tumbling.CountAndTimeWindowState;
import lombok.Generated;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class WorkloadShedderOnQueue extends WorkloadShedder {

    /**
     * 配置：平均排队时长多大被认为过载.
     */
    @Heuristics
    public static final long AVG_QUEUED_MS_UPPER_BOUND = 200;

    /**
     * 最近一次显式过载的时间.
     */
    private volatile long overloadedAtNs = 0;

    private final long timeCycleNs;

    WorkloadShedderOnQueue(String name) {
        super(name);
        this.timeCycleNs = windowConfig().getTimeCycleNs();
    }

    @Override
    protected boolean isOverloaded(long nowNs, @NonNull CountAndTimeWindowState windowState) {
        // 距离上次显式过载仍在窗口期
        boolean stillExplicitOverloaded = nowNs > 0 && overloadedAtNs > 0
                && (nowNs - overloadedAtNs) <= timeCycleNs;
        boolean overloaded = stillExplicitOverloaded
                || windowState.avgQueuedMs() > AVG_QUEUED_MS_UPPER_BOUND; // 排队时间长
        if (overloaded) {
            log.warn("[{}] overloaded: {}", name, stillExplicitOverloaded ? "within explicit overload interval" : "too large avg queued time");
        }
        return overloaded;
    }

    void addWaitingNs(long waitingNs) {
        currentWindow().waitNs(waitingNs);
    }

    void overload(long overloadedAtNs) {
        log.info("[{}] got explicit overload feedback", name);
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
