package io.github.workload.overloading;

import io.github.workload.WorkloadPriority;
import io.github.workload.annotations.Heuristics;
import io.github.workload.annotations.ThreadSafe;
import lombok.NonNull;

/**
 * 基于反馈控制原理的自适应式工作负载准入控制器.
 *
 * <p>Regulate incoming requests and shed excess trivial workload.</p>
 * <p>Providing provision leverage effect，按照{@link WorkloadPriority}提高资源分时复用的利用率，降低整体资源成本</p>
 */
@ThreadSafe
public interface AdmissionController {
    /**
     * CPU使用率到达多少被认为CPU过载.
     *
     * <p>75%</p>
     */
    @Heuristics
    double CPU_USAGE_UPPER_BOUND = 0.75;

    /**
     * CPU过载判断的静默期(in second)：解决启动时CPU飙高导致的误判断.
     *
     * <p>15分钟</p>
     */
    @Heuristics
    long CPU_OVERLOAD_COOL_OFF_SEC = 15 * 60;

    /**
     * 获取指定类型的准入控制器实例.
     *
     * <p>对于相同的类型，返回的是同一份实例：以类型为单位的单例.</p>
     *
     * @param name name(or type) of the admission control
     */
    static AdmissionController getInstance(@NonNull String name) {
        return AdmissionControllerFactory.getInstance(name,
                () -> new DAGORAdmissionController(name));
    }

    /**
     * 决定工作负荷是否准入.
     *
     * <p>只做判断，由调用者针对结果决定如何处理.</p>
     * <p>例如，上层定义优先级时可能需要{@code SHED_THRESHOLD}，高于它的工作负载不能抛弃，这部分逻辑可以在上层实现.</p>
     * <pre>
     * {@code
     *
     * if (!admissionController.admit(priority) && priority < SHED_THRESHOLD) {
     *     // drop the request
     * }
     * }
     * </pre>
     *
     * @param priority priority of the workload
     * @return true if admitted, or else rejected
     */
    boolean admit(@NonNull WorkloadPriority priority);

    /**
     * Feedback of workload.
     *
     * @param feedback 反馈
     */
    void feedback(@NonNull WorkloadFeedback feedback);

}
