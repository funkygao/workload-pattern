package io.github.workload.overloading;

import lombok.NonNull;

/**
 * 基于反馈控制原理的自适应式工作负载准入控制器.
 *
 * <p>Regulate incoming requests and shed excess workload.</p>
 * <p>Providing provision leverage effect，按照{@link WorkloadPriority}提高资源分时复用的利用率，降低整体资源成本</p>
 */
public interface AdmissionController {

    /**
     * 获取指定类型的准入控制器实例.
     *
     * <p>对于相同的类型，返回的是同一份实例：以类型为单位的单例.</p>
     */
    static AdmissionController getInstance(String kind) {
        return FairSafeAdmissionController.getInstance(kind);
    }

    /**
     * 决定工作负荷是否准入.
     *
     * <p>只做判断，由调用者针对结果决定如何处理.</p>
     *
     * @param priority priority of the workload
     * @return true if admitted, or else rejected
     */
    boolean admit(@NonNull WorkloadPriority priority);

    /**
     * 直接进入过载状态：显式过载反馈.
     */
    void overloaded();

    /**
     * 汇报工作负荷的排队时长：隐式过载检测.
     *
     * @param queuedNs
     */
    void recordQueuedNs(long queuedNs);
}
