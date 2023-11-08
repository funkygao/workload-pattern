package io.github.workload.overloading;

import lombok.AccessLevel;
import lombok.Getter;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 基于(时间周期，请求数量周期)的滑动窗口，窗口间不重叠.
 *
 * <pre>
 * +────────────────+────────────────+────
 * │ window1        │ window2        │ ...
 * +────────────────+────────────────+────
 *  |         |
 * startNs  nowNs
 * </pre>
 */
class SlidingWindow {
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
    private AtomicLong accumulatedWaitNs = new AtomicLong(0);

    SlidingWindow() {
        this(DefaultTimeCycleNs, DefaultRequestCycle);
    }

    SlidingWindow(long timeCycleNs, int requestCycle) {
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
        accumulatedWaitNs.addAndGet(waitingNs);
    }

    long avgQueuingTimeMs() {
        int requests = requestCounter.get();
        if (requests == 0) {
            // avoid divide by zero
            return 0;
        }

        return accumulatedWaitNs.get() / (requests * NsPerMs);
    }

    void slide(long nowNs) {
        startNs = nowNs;
        requestCounter.set(0);
        admittedCounter.set(0);
        accumulatedWaitNs.set(0);
    }

    int admitted() {
        return admittedCounter.get();
    }

}
