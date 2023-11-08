package io.github.workload.overloading;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.RejectedExecutionHandler;

/**
 * 面向QoS的自适应式工作负载准入管制.
 *
 * <pre>
 *                         AdmissionController
 *                   IN  +───────────────────────+  OUT
 * WorkloadPriority ───> │ AdmissionLevel        │ ─────> deny/accept
 *                       +───────────────────────+
 *                       │ +admit(P)             │
 *                       │ +recordQueuedNs(ns)   │
 *                       │ +markOverloaded()     │
 *                       +───────────────────────+
 *                        window: (time, counter)
 *                   load status: avg queuing time/overloaded
 * </pre>
 *
 * <p>Note：该过载保护仅适用于brief bursts，如果系统长期过载，应该扩容或改进设计.</p>
 * <ul>过载检测有2种：
 * <li>显式，例如线程池满，MQ积压超过阈值等：{@link AdmissionController#markOverloaded()}</li>
 * <li>隐式，更准确，但需要系统可以检测工作负载的排队时长：{@link AdmissionController#recordQueuedNs(long)}</li>
 * </ul>
 *
 * @see <a href="https://www.cs.columbia.edu/~ruigu/papers/socc18-final100.pdf">微信的过载保护</a>
 * @see <a href="https://www.usenix.org/legacy/publications/library/proceedings/usits03/tech/full_papers/welsh/welsh_html/usits.html">Adaptive Overload Control for Busy Internet Servers</a>
 */
@Slf4j
public class AdmissionController {
    final OverloadDetector overloadDetector;

    public AdmissionController() {
        this(200);
    }

    /**
     * Constructor.
     *
     * @param overloadQueuingMs 平均排队时间超过该值则认为系统已经过载
     */
    public AdmissionController(long overloadQueuingMs) {
        overloadDetector = new OverloadDetector(overloadQueuingMs);
    }

    public void setWindowSlideHook(WindowSlideHook hook) {
        overloadDetector.hook = hook;
    }

    /**
     * 动态调整请求排队时间阈值.
     *
     * <p>这是个经验值，因此实践中通常集成动态配置中心，尝试不同阈值摸索出最佳值.</p>
     */
    public void adjustOverloadQueuingMs(long overloadQueuingMs) {
        overloadDetector.overloadQueuingMs = overloadQueuingMs;
    }

    /**
     * 根据当前的{@link AdmissionLevel}水位，决定工作负荷是否准入.
     *
     * @param workloadPriority 工作负荷优先级
     * @return false if denied
     */
    public boolean admit(@NonNull WorkloadPriority workloadPriority) {
        return overloadDetector.admit(workloadPriority);
    }

    /**
     * 汇报工作负荷的排队时长：隐式过载检测.
     *
     * @param queuedNs
     */
    public void recordQueuedNs(long queuedNs) {
        overloadDetector.addWaitingNs(queuedNs);
    }

    /**
     * 直接进入过载状态：显式过载检测.
     *
     * <p>例如，线程池满，CPU使用率过高等</p>
     * <p>考虑到JVM运行在容器等复杂环境，CPU是否过载由应用自行判断</p>
     */
    public void markOverloaded() {
        overloadDetector.overloadedAtNs = System.nanoTime();
    }

    public RejectedExecutionHandler rejectedExecutionHandler() {
        return (runnable, executor) -> {
            // 显式过载
            markOverloaded();
        };
    }
}
