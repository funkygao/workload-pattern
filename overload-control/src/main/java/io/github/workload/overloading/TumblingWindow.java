package io.github.workload.overloading;

import io.github.workload.annotations.ThreadSafe;
import io.github.workload.annotations.VisibleForTesting;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicReference;

/**
 * 基于(时间周期，请求数量周期)的滚动窗口，用于对工作负荷采样.
 */
@Slf4j
class TumblingWindow {
    private final String name;

    @Getter(AccessLevel.PACKAGE)
    private final WindowConfig config;

    private final AtomicReference<WindowState> current;

    TumblingWindow(long startNs, String name, WindowConfig config) {
        this.config = config;
        this.name = name;
        this.current = new AtomicReference<>(new WindowState(startNs));
        log.info("[{}] created with {}", name, config);
    }

    @VisibleForTesting
    WindowState current() {
        return current.get();
    }

    @ThreadSafe
    void advance(WorkloadPriority workloadPriority, boolean admitted, long nowNs) {
        WindowState currentWindow = current();
        currentWindow.sample(workloadPriority, admitted);
        if (currentWindow.outOfRange(nowNs, config)) {
            swapWindow(nowNs, currentWindow);
        }
    }

    @ThreadSafe
    private void swapWindow(long nowNs, WindowState currentWindow) {
        // initiate swapping if needed
        if (!currentWindow.tryAcquireSwappingLock()) {
            // 没有获取切换权的线程，已经把统计数据采样到了当前窗口
            return;
        }

        WindowState nextWindow = new WindowState(nowNs);
        if (current.compareAndSet(currentWindow, nextWindow)) {
            if (log.isDebugEnabled()) {
                log.debug("[{}] after:{}ms, swapped window:{}, admitted:{}/{}",
                        name, currentWindow.ageMs(nowNs),
                        currentWindow.hashCode(),
                        currentWindow.admitted(), currentWindow.requested());
            }

            config.getOnWindowSwap().accept(nowNs, currentWindow);
            currentWindow.cleanup();
        }
    }

    @ThreadSafe
    void sampleWaitingNs(long waitingNs) {
        current().waitNs(waitingNs);
    }

}
