package io.github.workload.overloading;

import io.github.workload.Workload;
import io.github.workload.annotations.ThreadSafe;
import io.github.workload.overloading.metrics.IMetricsTrackerFactory;
import lombok.NonNull;

/**
 * 基于反馈控制原理的自适应式工作负载准入控制器.
 *
 * <p>Regulate incoming requests and shed excess trivial workload.</p>
 */
@ThreadSafe
public interface AdmissionController {

    /**
     * 决定工作负荷是否准入.
     *
     * @param workload the computational workload
     * @return true if admitted, or else rejected
     */
    boolean admit(@NonNull Workload workload);

    /**
     * Feedback of workload from the application.
     *
     * @param feedback 反馈
     */
    void feedback(@NonNull WorkloadFeedback feedback);

    /**
     * 获取指定类型的准入控制器实例，名称粒度的单例.
     *
     * @param name name(or type) of the admission control
     */
    static AdmissionController getInstance(@NonNull String name) {
        return getInstance(name, null);
    }

    /**
     * 获取指定类型的准入控制器实例并指定指标采集器工厂，名称粒度的单例.
     *
     * @param name                  name(or type) of the admission control
     * @param metricsTrackerFactory factory that creates metrics tracker
     */
    static AdmissionController getInstance(@NonNull String name, IMetricsTrackerFactory metricsTrackerFactory) {
        return AdmissionControllerFactory.getInstance(name,
                () -> new FairSafeAdmissionController(name, metricsTrackerFactory));
    }

}
