package io.github.workload.overloading;

import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static io.github.workload.overloading.WindowConfig.NS_PER_MS;

class WindowState {
    private final long startNs;
    private final AtomicInteger requestCounter;
    private final AtomicInteger admittedCounter;
    private final AtomicLong accumulatedQueuedNs;
    private final ConcurrentSkipListMap<Integer, AtomicInteger> histogram;

    private final AtomicBoolean swappingLock;

    WindowState(long startNs) {
        this.startNs = startNs;
        this.requestCounter = new AtomicInteger(0);
        this.admittedCounter = new AtomicInteger(0);
        this.accumulatedQueuedNs = new AtomicLong(0);
        this.histogram = new ConcurrentSkipListMap<>();
        this.swappingLock = new AtomicBoolean(false);
    }

    ConcurrentSkipListMap<Integer, AtomicInteger> histogram() {
        return histogram;
    }

    // enforce that only a single thread can initiate the process to swap out the current window
    boolean tryAcquireSwappingLock() {
        return swappingLock.compareAndSet(false, true);
    }

    void sample(WorkloadPriority workloadPriority, boolean admitted) {
        requestCounter.incrementAndGet();
        if (admitted) {
            admittedCounter.incrementAndGet();
        }
        AtomicInteger prioritizedCounter = histogram.computeIfAbsent(workloadPriority.P(), key -> new AtomicInteger(0));
        prioritizedCounter.incrementAndGet();
    }

    void waitNs(long waitingNs) {
        accumulatedQueuedNs.addAndGet(waitingNs);
    }

    void cleanup() {
        histogram.clear();
    }

    int requested() {
        return requestCounter.get();
    }

    int admitted() {
        return admittedCounter.get();
    }

    boolean outOfRange(long nowNs, WindowConfig config) {
        return requested() > config.getRequestCycle() ||
                (nowNs - startNs) > config.getTimeCycleNs();
    }

    long ageMs(long nowNs) {
        return (nowNs - startNs) / NS_PER_MS;
    }

    long avgQueuedMs() {
        int requested = requested();
        if (requested == 0) {
            // avoid divide by zero
            return 0;
        }

        return accumulatedQueuedNs.get() / (requested * NS_PER_MS);
    }
}
