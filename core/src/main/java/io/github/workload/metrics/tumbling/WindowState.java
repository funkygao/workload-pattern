package io.github.workload.metrics.tumbling;

import io.github.workload.annotations.ThreadSafe;
import io.github.workload.annotations.VisibleForTesting;
import io.github.workload.WorkloadPriority;
import lombok.Generated;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;

/**
 * 窗口状态的存储.
 */
@ThreadSafe
public abstract class WindowState {
    /**
     * 该窗口期的总请求数量.
     */
    private final LongAdder requestCounter;

    private final AtomicBoolean rolloverLock;

    protected WindowState() {
        requestCounter = new LongAdder();
        rolloverLock = new AtomicBoolean(false);
    }

    /**
     * 对给定的工作负荷进行采样，以便切换窗口时统计分析.
     *
     * @param priority 工作负荷优先级
     * @param admitted 是否准入
     */
    protected abstract void doSample(WorkloadPriority priority, boolean admitted);

    /**
     * 窗口被换出且不再使用情况下的资源清理.
     *
     * <p>虽然GC会自动清楚不再使用的资源，但窗口频繁切换情况下，不及时主动清理可能导致内存压力增大，甚至OOM</p>
     */
    protected void cleanup() {
        // leave for children
    }

    /**
     * 当前窗口切换到新窗口的日志输出.
     */
    protected void logRollover(String prefix, long nowNs, WindowState nextWindow, WindowConfig config) {
        // leave for children
    }

    @VisibleForTesting
    @Generated
    protected synchronized void resetForTesting() {
        requestCounter.reset();
        rolloverLock.set(false);
    }

    /**
     * 当前线程获取窗口切换权.
     *
     * <p>enforce that only a single thread can initiate the process to swap out the current window.</p>
     * @return true if granted
     */
    boolean tryAcquireRolloverLock() {
        // 由于窗口被原子性地切换，该锁无需释放
        return rolloverLock.compareAndSet(false, true);
    }

    final void sample(WorkloadPriority priority, boolean admitted) {
        requestCounter.increment();
        doSample(priority, admitted);
    }

    public final int requested() {
        return requestCounter.intValue();
    }
}
