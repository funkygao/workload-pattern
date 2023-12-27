package io.github.workload.window;

import io.github.workload.overloading.WorkloadPriority;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;

public abstract class WindowState {
    private final LongAdder requestCounter;
    private final AtomicBoolean swappingLock;

    WindowState() {
        requestCounter = new LongAdder();
        swappingLock = new AtomicBoolean(false);
    }

    protected abstract void doSample(WorkloadPriority priority);

    protected abstract void doSample(WorkloadPriority priority, boolean admitted);

    abstract void cleanup();

    // enforce that only a single thread can initiate the process to swap out the current window
    boolean tryAcquireSwappingLock() {
        // 由于窗口被原子性地切换，该锁无需释放
        return swappingLock.compareAndSet(false, true);
    }

    final void sample(WorkloadPriority priority) {
        requestCounter.increment();
        doSample(priority);
    }

    void sample(WorkloadPriority priority, boolean admitted) {
        requestCounter.increment();
        doSample(priority, admitted);
    }

    public final int requested() {
        return requestCounter.intValue();
    }
}
