package io.github.workload.overloading;

import lombok.AccessLevel;
import lombok.Setter;

class WorkloadShedderOnQueue extends WorkloadShedder {

    /**
     * 配置：平均排队时长多大被认为过载.
     *
     * <p>由于是配置，没有加{@code volatile}</p>
     */
    private long overloadQueuingMs = 200;

    /**
     * 最近一次显式过载的时间.
     */
    @Setter(AccessLevel.PACKAGE)
    private volatile long overloadedAtNs = 0;

    WorkloadShedderOnQueue() {
        super();
    }

    @Override
    protected boolean isOverloaded(long nowNs) {
        return window.avgQueuedMs() > overloadQueuingMs // 排队时间长
                || (nowNs - overloadedAtNs) <= window.getTimeCycleNs(); // 距离上次显式过载仍在窗口期
    }

    void addWaitingNs(long waitingNs) {
        if (windowSwapLock.get()) {
            // 正在滑动窗口，即使计数也可能被重置
            return;
        }

        window.sampleWaitingNs(waitingNs);
    }

}
