package io.github.workload.overloading;

/**
 * 自适应的服务负载准入管制.
 *
 * <pre>
 *                         AdmissionController
 *                   IN  +───────────────────────+  OUT
 * WorkloadPriority ───> │ admissionLevel/cursor │ ─────> deny/accept
 *                       +───────────────────────+
 *                       │ +admit(P)             │
 *                       │ +recordQueuedNs(ns)   │
 *                       │ +markOverloaded()     │
 *                       +───────────────────────+
 *                        window: (time, counter)
 *                   load status: queuing time
 * </pre>
 *
 * <p>
 * Load variations and bursts of traffic often cause an application’s computational demands to exceed
 * allocated system resources, causing requests and tasks to linger in queues and, eventually, violate
 * service level objectives (SLOs).
 * </p>
 * <p>
 * Data center workloads are often heavy-tailed and characterized by
 * rare, expensive requests intermingled with many, simple ones.
 * </p>
 * <p>需要注意的是，该过载保护仅适用于brief bursts，如果系统长期过载，应该扩容或改进设计.</p>
 *
 * @see <a href="https://www.cs.columbia.edu/~ruigu/papers/socc18-final100.pdf">微信的过载保护</a>
 * @see <a href="https://cloud.redhat.com/blog/surviving-the-api-storm-with-api-priority-fairness">K8S APF配置</a>
 * @see <a href="https://www.usenix.org/legacy/publications/library/proceedings/usits03/tech/full_papers/welsh/welsh_html/usits.html">Adaptive Overload Control for Busy Internet Servers</a>
 */
public class AdmissionController {
    /**
     * 对于微信，服务的默认超时是500ms，对应的平均排队阈值设置为20ms，超过该值则认为服务过载.
     */
    private static final long defaultOverloadQueuingMs = 200;

    // TODO ducc, dryrun to find the empirical configuration
    private long overloadQueuingMs = defaultOverloadQueuingMs;

    private final OverloadDetector overloadDetector = new OverloadDetector(defaultOverloadQueuingMs);

    public void setWindowSlideHook(WindowSlideHook hook) {
        overloadDetector.hook = hook;
    }

    /**
     * 根据当前admission level，决定请求是否准入.
     *
     * @param workloadPriority 新请求的优先级
     * @return false if denied
     */
    public boolean admit(WorkloadPriority workloadPriority) {
        return overloadDetector.admit(workloadPriority);
    }

    /**
     * 运维功能：动态调整请求排队时间阈值.
     */
    public void adjustOverloadQueuingMs(long overloadQueuingMs) {
        overloadDetector.adjustOverloadQueuingMs(overloadQueuingMs);
    }

    public WorkloadPriority localAdmissionLevel() {
        return overloadDetector.getLocalAdmissionLevel().priority();
    }

    /**
     * 汇报请求的排队时长.
     *
     * @param queuedNs
     */
    public void recordQueuedNs(long queuedNs) {
        overloadDetector.addWaitingNs(queuedNs);
    }

    /**
     * 直接进入过载状态.
     *
     * <p>例如，线程池已满 {@link java.util.concurrent.RejectedExecutionHandler}</p>
     */
    public void markOverloaded(boolean overloaded) {
        overloadDetector.overloaded = overloaded;
    }

    OverloadDetector detector() {
        return overloadDetector;
    }

}
