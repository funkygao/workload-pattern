package io.github.workload.overloading;

import io.github.workload.annotations.NotThreadSafe;
import lombok.AccessLevel;
import lombok.Getter;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 基于(时间周期，请求数量周期)的滚动窗口，窗口间不重叠.
 *
 * <pre>
 * │<- requestCycle->│
 * │<- timeCycleNs ->│
 * +─────────────────+─────────────────+────
 * │ window1/metrics │ window2/metrics │ ...
 * +─────────────────+─────────────────+────
 * │         │
 * startNs  nowNs
 * </pre>
 */
@NotThreadSafe
class MetricsRollingWindow {
    static final long NsPerMs = TimeUnit.NANOSECONDS.convert(1, TimeUnit.MILLISECONDS);

    static final long DefaultTimeCycleNs = TimeUnit.NANOSECONDS.convert(1, TimeUnit.SECONDS); // 1s
    static final int DefaultRequestCycle = 2 << 10; // 2K

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

    MetricsRollingWindow(long timeCycleNs, int requestCycle) {
        this.timeCycleNs = timeCycleNs;
        this.requestCycle = requestCycle;
        this.startNs = System.nanoTime();
    }

    void tick(boolean admitted) {
        requestCounter.incrementAndGet();
        if (admitted) {
            admittedCounter.getAndIncrement();
        }
    }

    boolean full(long nowNs) {
        return nowNs - startNs > timeCycleNs // 时间满足
                || requestCounter.get() > requestCycle; // 请求数量满足
    }

    void addWaitingNs(long waitingNs) {
        accumulatedQueuedNs.addAndGet(waitingNs);
    }

    long avgQueuedMs() {
        int requests = requestCounter.get();
        if (requests == 0) {
            // avoid divide by zero
            return 0;
        }

        return accumulatedQueuedNs.get() / (requests * NsPerMs);
    }

    void slide(long nowNs) {
        startNs = nowNs;
        requestCounter.set(0);
        admittedCounter.set(0);
        accumulatedQueuedNs.set(0);
    }

    int admitted() {
        return admittedCounter.get();
    }

}
