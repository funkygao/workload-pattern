package io.github.workload.overloading;

import io.github.workload.window.CountAndTimeWindowState;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class WorkloadShedderOnQueue extends WorkloadShedder {

    /**
     * 配置：平均排队时长多大被认为过载.
     *
     * <p>由于是配置，没有加{@code volatile}</p>
     */
    private final long overloadQueuingMs = 200;

    /**
     * 最近一次显式过载的时间.
     */
    private volatile long overloadedAtNs = 0;

    private final long timeCycleNs;

    WorkloadShedderOnQueue(String name) {
        super(name);
        this.timeCycleNs = window.getConfig().getTimeCycleNs();
    }

    @Override
    protected boolean isOverloaded(long nowNs, CountAndTimeWindowState windowState) {
        return windowState.avgQueuedMs() > overloadQueuingMs // 排队时间长
                || (nowNs - overloadedAtNs) <= timeCycleNs; // 距离上次显式过载仍在窗口期
    }

    void addWaitingNs(long waitingNs) {
        window.current().waitNs(waitingNs);
    }

    void overload(long overloadedAtNs) {
        log.trace("[{}] got explicit overload event", name);
        this.overloadedAtNs = overloadedAtNs;
    }

}
