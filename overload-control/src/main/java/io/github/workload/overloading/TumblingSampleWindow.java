package io.github.workload.overloading;

import io.github.workload.annotations.ThreadSafe;
import io.github.workload.annotations.VisibleForTesting;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * 基于(时间周期，请求数量周期)的滚动窗口，用于对工作负荷采样.
 *
 * <p>lockless</p>
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
     * 当前窗口所有请求的总排队时长.
     */
    private AtomicLong accumulatedQueuedNs = new AtomicLong(0);

    // 确保窗口数据的原子性更新
    private final AtomicReference<WindowData> windowDataRef;



    TumblingSampleWindow(long startNs, String name) {
        this(DEFAULT_TIME_CYCLE_NS, DEFAULT_REQUEST_CYCLE, startNs, name);
    }

    private TumblingSampleWindow(long timeCycleNs, int requestCycle, long startNs, String name) {
        this.timeCycleNs = timeCycleNs;
        this.requestCycle = requestCycle;
        this.name = name;
        this.windowDataRef = new AtomicReference<>(new WindowData(startNs));
        log.info("[{}] time cycle {}s, request cycle:{}", name, timeCycleNs / NS_PER_MS / 1000, requestCycle);
    }

    @ThreadSafe
    void sample(WorkloadPriority workloadPriority, boolean admitted, long nowNs, Consumer<Long> onWindowSwap) {
        WindowData currentWindow = windowDataRef.get();
        if (admitted) {
            currentWindow.admittedCounter.incrementAndGet();
        }

        AtomicInteger prioritizedCounter = currentWindow.histogram.computeIfAbsent(workloadPriority.P(), key -> new AtomicInteger(0));
        prioritizedCounter.incrementAndGet();

        if (currentWindow.requestCounter.incrementAndGet() >= requestCycle ||
                nowNs - currentWindow.startNs > timeCycleNs) {
            swapWindow(nowNs, onWindowSwap);
        }
    }


    /*
     * This code uses an 'AtomicReference' to maintain the state of the current window,
     * which allows us to atomically swap it for a new window.
     * The 'sample' method checks if the window should be swapped and delegates the swap
     * to the 'swapWindow' method.
     * The 'swapWindow' method attempts to swap the window using a CAS operation on the
     * 'windowDataRef', thus ensuring thread-safety without the use of traditional
     * locks or synchronization blocks.
     */
    private void swapWindow(long nowNs, Consumer<Long> onWindowSwap) {
        WindowData currentWindow = windowDataRef.get();
        if (!currentWindow.swapping.compareAndSet(false, true)) {
            log.info("9");
            return; // Another thread is already swapping the window, no need to continue.
        }

        if (full(nowNs, currentWindow)) {
            WindowData newWindow = new WindowData(nowNs);
            if (windowDataRef.compareAndSet(currentWindow, newWindow)) {
                log.info("[{}] after:{}ms, swap: {}/{}", name, currentWindow.age(nowNs), currentWindow.admitted(), currentWindow.requested());

                onWindowSwap.accept(nowNs);
                currentWindow.cleanup();
                log.info("111");
            } else {
                log.info("88");
            }
        }
        log.info("22");
        currentWindow.swapping.set(false); // Done swapping, or no swap was needed.


    }



    @ThreadSafe
    boolean full(long nowNs, WindowData currentWindow) {
        return nowNs - currentWindow.startNs > timeCycleNs ||
                currentWindow.requestCounter.get() >= requestCycle;
    }

    @ThreadSafe
    void sampleWaitingNs(long waitingNs) {
        accumulatedQueuedNs.addAndGet(waitingNs);
    }

    @ThreadSafe
    int admitted() {
        return windowDataRef.get().admittedCounter.get();
    }

    ConcurrentSkipListMap<Integer, AtomicInteger> histogram() {
        return windowDataRef.get().histogram;
    }


    long avgQueuedMs() {
        int requests = windowDataRef.get().requestCounter.get();
        if (requests == 0) {
            // avoid divide by zero
            return 0;
        }

        return accumulatedQueuedNs.get() / (requests * NS_PER_MS);
    }


    private static final class WindowData {
        final long startNs;
        final AtomicInteger requestCounter;
        final AtomicInteger admittedCounter;
        final AtomicBoolean swapping;
        final ConcurrentSkipListMap<Integer, AtomicInteger> histogram;

        WindowData(long startNs) {
            this.startNs = startNs;
            this.requestCounter = new AtomicInteger(0);
            this.admittedCounter = new AtomicInteger(0);
            this.swapping = new AtomicBoolean(false);
            this.histogram = new ConcurrentSkipListMap<>();
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

        long age(long nowNs) {
            return (nowNs - startNs) / NS_PER_MS;
        }
    }
}
