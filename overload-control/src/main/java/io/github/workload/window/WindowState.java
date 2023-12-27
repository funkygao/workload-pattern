package io.github.workload.window;

import io.github.workload.annotations.ThreadSafe;
import io.github.workload.overloading.WorkloadPriority;

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

    private final AtomicBoolean swappingLock;

    protected WindowState() {
        requestCounter = new LongAdder();
        swappingLock = new AtomicBoolean(false);
    }

    /**
     * 对给定的工作负荷进行采样，以便切换窗口时统计分析.
     *
     * @param priority 工作负荷优先级
     * @param admitted 是否准入
     */
    protected abstract void doSample(WorkloadPriority priority, boolean admitted);

    abstract void cleanup();

    /**
     * 当前窗口切换到新窗口的日志输出.
     */
    abstract void logSwapping(String prefix, long nowNs, WindowState nextWindow, WindowConfig config);

    /**
     * 当前线程获取窗口切换权.
     *
     * <p>enforce that only a single thread can initiate the process to swap out the current window.</p>
     * @return true if granted
     */
    boolean tryAcquireSwappingLock() {
        // 由于窗口被原子性地切换，该锁无需释放
        return swappingLock.compareAndSet(false, true);
    }

    final void sample(WorkloadPriority priority, boolean admitted) {
        requestCounter.increment();
        doSample(priority, admitted);
    }

    public final int requested() {
        return requestCounter.intValue();
    }
}
