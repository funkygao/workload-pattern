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
class TumblingSampleWindow {
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

    TumblingSampleWindow(long startNs, String name) {
        this(DEFAULT_TIME_CYCLE_NS, DEFAULT_REQUEST_CYCLE, startNs, name);
    }

    private TumblingSampleWindow(long timeCycleNs, int requestCycle, long startNs, String name) {
        this.timeCycleNs = timeCycleNs;
        this.requestCycle = requestCycle;
        this.startNs = startNs;
        this.name = name;
        log.info("[{}] time cycle {}s, request cycle:{}", name, timeCycleNs / NS_PER_MS / 1000, requestCycle);
    }

    @ThreadSafe
    boolean sample(WorkloadPriority workloadPriority, boolean admitted, long nowNs) {
        boolean full = false;
        if (nowNs - startNs > timeCycleNs) {
            // 时间周期到了
            full = true;
        }
        if (requestCounter.incrementAndGet() >= requestCycle) {
            // 并发场景下，一个窗口内的实际请求数量可能超过requestCycle，这些请求被采样到当前窗口
            full = true;
        }

        if (admitted) {
            admittedCounter.incrementAndGet();
        }

        AtomicInteger prioritizedCounter = histogram.computeIfAbsent(workloadPriority.P(), key -> {
            log.trace("[{}] histogram for new P:{}, admitted:{}", name, key, admitted);
            return new AtomicInteger(0);
        });
        prioritizedCounter.incrementAndGet();
        return full;
    }

    @ThreadSafe
    boolean full(long nowNs) {
        return nowNs - startNs > timeCycleNs // 时间满足
                || requestCounter.get() >= requestCycle; // 请求数量满足
    }

    @ThreadSafe
    void sampleWaitingNs(long waitingNs) {
        accumulatedQueuedNs.addAndGet(waitingNs);
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
        if (log.isDebugEnabled()) {
            log.debug("[{}] restart after:{}ms, admitted:{}/{}", name, (nowNs - startNs) / NS_PER_MS, admitted(), requestCounter.get());
        }

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
        return "Window(request=" + requestCounter.get() + ",admit=" + admittedCounter.get() + ")";
    }

}
