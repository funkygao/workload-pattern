package io.github.workload.overloading;

/**
 * 工作负荷的反馈：显式，隐式.
 */
public interface WorkloadFeedback {

    /**
     * 直接进入过载状态：显式过载反馈.
     */
    static WorkloadFeedback ofOverloaded() {
        return new WorkloadFeedbackOverloaded(System.nanoTime());
    }

    /**
     * 汇报工作负荷的排队时长：隐式过载检测.
     *
     * @param queuedNs queued time in nano seconds
     */
    static WorkloadFeedback ofQueuedNs(long queuedNs) {
        return new WorkloadFeedbackQueued((queuedNs));
    }
}
