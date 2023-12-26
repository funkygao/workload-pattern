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
        // 由于并发，可能采样时没有满，而计算outOfRange时已经满了：被其他线程采样进来的
        if (currentWindow.outOfRange(nowNs, config)) {
            trySwapWindow(nowNs, currentWindow);
        }
    }

    @ThreadSafe
    private void trySwapWindow(long nowNs, WindowState currentWindow) {
        if (!currentWindow.tryAcquireSwappingLock()) {
            // offers an early exit to avoid unnecessary preparation for the swap
            return;
        }

        WindowState nextWindow = new WindowState(nowNs);
        if (current.compareAndSet(currentWindow, nextWindow)) {
            // 此后，采样数据都进入新窗口，currentWindow 内部状态不会再变化
            if (log.isDebugEnabled()) {
                // 日志的输出顺序可能与实际的窗口切换顺序不一致
                // 窗口切换的顺序：a -> b -> c，但日志可能是：b -> c, a -> b
                // ThreadA，完成切换：a -> b，但在日志输出前被阻塞或者调度另一个线程
                // ThreadB，完成切换：b -> c，并且输出了日志
                // ThreadA被调度，输出日志
                // 如果在compareAndSet前面输出日志，那么该日志顺序与窗口切换顺序一定一致
                log.debug("[{}] after:{}ms, swapped window:{} -> {}, admitted:{}/{}, delta:{}",
                        name, currentWindow.ageMs(nowNs),
                        currentWindow.hashCode(), nextWindow.hashCode(),
                        currentWindow.admitted(), currentWindow.requested(),
                        currentWindow.requested() - config.getRequestCycle());
            }

            // 此时的 currentWindow 是该窗口的最终值
            config.getOnWindowSwap().accept(nowNs, currentWindow);
            currentWindow.cleanup();
        } else {
            // should never happen
            log.error("Expected to swap the window but the compareAndSet operation failed.");
        }
    }

    @ThreadSafe
    void sampleWaitingNs(long waitingNs) {
        current().waitNs(waitingNs);
    }

}
