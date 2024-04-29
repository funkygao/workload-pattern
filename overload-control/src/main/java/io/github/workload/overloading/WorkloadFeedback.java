package io.github.workload.overloading;

import lombok.Getter;

/**
 * 工作负荷的反馈.
 */
public interface WorkloadFeedback {

    /**
     * 直接进入过载状态：显式过载反馈.
     *
     * <p>相当于TCP的ECN(Explicit Congestion Notification).</p>
     */
    static WorkloadFeedback ofOverloaded() {
        return new Overload(System.nanoTime());
    }

    /**
     * 汇报工作负荷的排队时长：隐式过载检测.
     *
     * @param queuedNs queued time in nano seconds
     */
    static WorkloadFeedback ofQueuedNs(long queuedNs) {
        return new Queued((queuedNs));
    }

    @Getter
    class Overload implements WorkloadFeedback {
        private final long overloadAtNs;

        Overload(long overloadAtNs) {
            this.overloadAtNs = overloadAtNs;
        }
    }


    @Getter
    class Queued implements WorkloadFeedback {
        private final long queuedNs;

        Queued(long queuedNs) {
            this.queuedNs = queuedNs;
        }
    }
}
