package io.github.workload.overloading;

import io.github.workload.Workload;
import io.github.workload.annotations.ThreadSafe;
import io.github.workload.overloading.metrics.IMetricsTrackerFactory;
import lombok.Getter;
import lombok.NonNull;

/**
 * 基于反馈控制原理的自适应式工作负荷准入控制器，前置保护.
 *
 * <p>Regulate incoming requests and shed excess trivial workload.</p>
 */
@ThreadSafe
public interface AdmissionController {

    /**
     * 决定工作负荷是否准入.
     *
     * <p>用于入口流量控制.</p>
     *
     * @param workload the computational workload
     * @return false表示当下如果执行该workload可能恶化系统负载，应用层根据场景做动作：可能直接解决请求，也可能降速，etc
     */
    boolean admit(@NonNull Workload workload);

    /**
     * Feedback of workload execution from the application.
     *
     * @param feedback 反馈
     */
    void feedback(@NonNull Feedback feedback);

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

    interface Feedback {
        /**
         * 直接进入过载状态：显式过载反馈.
         *
         * <p>相当于TCP的ECN(Explicit Congestion Notification).</p>
         */
        static Feedback ofOverloaded() {
            return new Overload(System.nanoTime());
        }

        /**
         * 反馈当前工作负荷的排队时长：隐式过载检测.
         *
         * @param queuedNs queued duration in nano seconds
         */
        static Feedback ofQueuedNs(long queuedNs) {
            return new Queued((queuedNs));
        }

        @Getter
        class Overload implements Feedback {
            private final long overloadAtNs;
            private Overload(long overloadAtNs) {
                this.overloadAtNs = overloadAtNs;
            }
        }

        @Getter
        class Queued implements Feedback {
            private final long queuedNs;
            private Queued(long queuedNs) {
                this.queuedNs = queuedNs;
            }
        }
    }
}
