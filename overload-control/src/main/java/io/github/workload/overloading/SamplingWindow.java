package io.github.workload.overloading;

import io.github.workload.annotations.NotThreadSafe;
import io.github.workload.annotations.ThreadSafe;
import io.github.workload.annotations.VisibleForTesting;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 基于(时间周期，请求数量周期)的滚动窗口，用于对工作负荷采样.
 */
@Slf4j
class SamplingWindow {
    @VisibleForTesting
    static final long NS_PER_MS = TimeUnit.MILLISECONDS.toNanos(1);

    @VisibleForTesting
    static final long DEFAULT_TIME_CYCLE_NS = System.getProperty("workload.window.DEFAULT_TIME_CYCLE_MS") != null ?
            TimeUnit.MILLISECONDS.toNanos(Long.valueOf(System.getProperty("workload.window.DEFAULT_TIME_CYCLE_MS"))) :
            TimeUnit.MILLISECONDS.toNanos(1000); // 1s

    @VisibleForTesting
    static final int DEFAULT_REQUEST_CYCLE = System.getProperty("workload.window.DEFAULT_REQUEST_CYCLE") != null ?
            Integer.valueOf(System.getProperty("workload.window.DEFAULT_REQUEST_CYCLE")) :
            2 << 10; // 2K

    private final String name;

    /**
     * 时间周期.
     */
    @Getter(AccessLevel.PACKAGE)
    private final long timeCycleNs;

    /**
     * 请求数量周期.
     */
    private final int requestCycle;

    /**
     * 当前窗口开始时间点.
     */
    private volatile long startNs;

    /**
     * 当前窗口的请求总量.
     */
    private AtomicInteger requestCounter = new AtomicInteger(0);

    /**
     * 当前窗口的放行请求总量.
     */
    private AtomicInteger admittedCounter = new AtomicInteger(0);

    /**
     * 当前窗口所有请求的总排队时长.
     */
    private AtomicLong accumulatedQueuedNs = new AtomicLong(0);

    /**
     * 各种优先级的请求数量分布.
     */
    private ConcurrentSkipListMap<Integer, AtomicInteger> histogram = new ConcurrentSkipListMap<>();

    SamplingWindow(long startNs, String name) {
        this(DEFAULT_TIME_CYCLE_NS, DEFAULT_REQUEST_CYCLE, startNs, name);
    }

    private SamplingWindow(long timeCycleNs, int requestCycle, long startNs, String name) {
        this.timeCycleNs = timeCycleNs;
        this.requestCycle = requestCycle;
        this.startNs = startNs;
        this.name = name;
    }

    @ThreadSafe
    void sample(WorkloadPriority workloadPriority, boolean admitted) {
        requestCounter.incrementAndGet();
        if (admitted) {
            admittedCounter.getAndIncrement();
        }

        AtomicInteger prioritizedCounter = histogram.computeIfAbsent(workloadPriority.P(), key -> {
            log.debug("[{}] histogram for new P:{}, admitted:{}", name, key, admitted);
            return new AtomicInteger(0);
        });
        prioritizedCounter.incrementAndGet();
    }

    @ThreadSafe
    void sampleWaitingNs(long waitingNs) {
        accumulatedQueuedNs.addAndGet(waitingNs);
    }

    @ThreadSafe
    boolean full(long nowNs) {
        return nowNs - startNs > timeCycleNs // 时间满足
                || requestCounter.get() > requestCycle; // 请求数量满足
    }

    @ThreadSafe
    int admitted() {
        return admittedCounter.get();
    }

    ConcurrentSkipListMap<Integer, AtomicInteger> histogram() {
        return histogram;
    }

    @NotThreadSafe(serial = true)
    void restart(long nowNs) {
        log.debug("[{}] restart after:{} ms, requests:{}", name, (nowNs - startNs) / NS_PER_MS, requestCounter.get());

        startNs = nowNs;
        requestCounter.set(0);
        admittedCounter.set(0);
        accumulatedQueuedNs.set(0);
        histogram.clear();
    }

    long avgQueuedMs() {
        int requests = requestCounter.get();
        if (requests == 0) {
            // avoid divide by zero
            return 0;
        }

        return accumulatedQueuedNs.get() / (requests * NS_PER_MS);
    }

    @Override
    public String toString() {
        // FIXME ConcurrentSkipListMap.size not O(1)
        return "Window(request=" + requestCounter.get() + ",admit=" + admittedCounter.get()
                + ",counters:" + histogram.size() + ")";
    }
}
