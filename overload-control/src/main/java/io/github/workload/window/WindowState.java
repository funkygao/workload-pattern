package io.github.workload.window;

import lombok.Getter;

import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

import static io.github.workload.window.WindowConfig.NS_PER_MS;

public class WindowState {
    @Getter
    private final long startNs;
    private final LongAdder requestCounter;
    private final LongAdder admittedCounter;
    private final AtomicLong accumulatedQueuedNs;
    // key is workload priority
    private final ConcurrentSkipListMap<Integer, AtomicInteger> histogram;

    private final AtomicBoolean swappingLock;

    WindowState(long startNs) {
        this.startNs = startNs;
        this.requestCounter = new LongAdder();
        this.admittedCounter = new LongAdder();
        this.accumulatedQueuedNs = new AtomicLong(0);
        this.histogram = new ConcurrentSkipListMap<>();
        this.swappingLock = new AtomicBoolean(false);
    }

    public ConcurrentSkipListMap<Integer, AtomicInteger> histogram() {
        return histogram;
    }

    // enforce that only a single thread can initiate the process to swap out the current window
    boolean tryAcquireSwappingLock() {
        // 由于窗口被原子性地切换，该锁无需释放
        return swappingLock.compareAndSet(false, true);
    }

    void sample(int workloadPriority) {
        requestCounter.increment();
        AtomicInteger prioritizedCounter = histogram.computeIfAbsent(workloadPriority, key -> new AtomicInteger(0));
        prioritizedCounter.incrementAndGet();
    }

    void sample(int workloadPriority, boolean admitted) {
        requestCounter.increment();
        if (admitted) {
            admittedCounter.increment();
        }
        AtomicInteger prioritizedCounter = histogram.computeIfAbsent(workloadPriority, key -> new AtomicInteger(0));
        prioritizedCounter.incrementAndGet();
    }

    void waitNs(long waitingNs) {
        accumulatedQueuedNs.addAndGet(waitingNs);
    }

    void cleanup() {
        histogram.clear();
    }

    public int requested() {
        return requestCounter.intValue();
    }

    public int admitted() {
        return admittedCounter.intValue();
    }

    boolean outOfRange(long nowNs, WindowConfig config) {
        return requested() > config.getRequestCycle() ||
                (nowNs - startNs) > config.getTimeCycleNs();
    }

    long ageMs(long nowNs) {
        return (nowNs - startNs) / NS_PER_MS;
    }

    public long avgQueuedMs() {
        int requested = requested();
        if (requested == 0) {
            // avoid divide by zero
            return 0;
        }

        return accumulatedQueuedNs.get() / (requested * NS_PER_MS);
    }
}
