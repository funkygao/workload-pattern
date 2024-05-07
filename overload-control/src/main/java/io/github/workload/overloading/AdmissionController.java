package io.github.workload.overloading;

import io.github.workload.Workload;
import io.github.workload.WorkloadPriority;
import io.github.workload.annotations.ThreadSafe;
import io.github.workload.overloading.metrics.IMetricsTrackerFactory;
import lombok.NonNull;

/**
 * 基于反馈控制原理的自适应式工作负载准入控制器.
 *
 * <p>Regulate incoming requests and shed excess trivial workload.</p>
 * <p>Providing provision leverage effect，按照{@link WorkloadPriority}提高资源分时复用的利用率，降低整体资源成本</p>
 */
@ThreadSafe
public interface AdmissionController {
    long CPU_OVERLOAD_COOL_OFF_SEC = JVM.getLong(JVM.CPU_OVERLOAD_COOL_OFF_SEC, 10 * 60);
    double CPU_USAGE_UPPER_BOUND = JVM.getDouble(JVM.CPU_USAGE_UPPER_BOUND, 0.75);

    /**
     * 获取指定类型的准入控制器实例.
     *
     * <p>对于相同的类型，返回的是同一份实例：以类型为单位的单例.</p>
     *
     * @param name name(or type) of the admission control
     * @return a singleton(by name) of the admission control instance
     */
    static AdmissionController getInstance(@NonNull String name) {
        return getInstance(name, null);
    }

    /**
     * 获取指定类型的准入控制器实例，并指定指标采集器工厂.
     *
     * <p>对于相同的类型，返回的是同一份实例：以类型为单位的单例.</p>
     *
     * @param name                  name(or type) of the admission control
     * @param metricsTrackerFactory factory that creates metrics tracker
     * @return a singleton(by name) of the admission control instance
     */
    static AdmissionController getInstance(@NonNull String name, IMetricsTrackerFactory metricsTrackerFactory) {
        return AdmissionControllerFactory.getInstance(name,
                () -> new FairSafeAdmissionController(name, metricsTrackerFactory));
    }

    /**
     * 决定工作负荷是否准入.
     *
     * @param workload the computational workload
     * @return true if admitted, or else rejected
     */
    boolean admit(@NonNull Workload workload);

    /**
     * 获取当前的准入等级水位线.
     *
     * <p>So that server can piggyback it to client, who can implement cheap client side admission control.</p>
     *
     * @return current admission level
     */
    default WorkloadPriority admissionLevel() {
        return null;
    }

    /**
     * Feedback of workload.
     *
     * @param feedback 反馈
     */
    void feedback(@NonNull WorkloadFeedback feedback);

}
