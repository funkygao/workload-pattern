package io.github.workload.window;

import io.github.workload.annotations.ThreadSafe;
import io.github.workload.overloading.WorkloadPriority;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicReference;

/**
 * 基于(时间周期，请求数量周期)的滚动窗口，用于对工作负荷采样.
 */
@Slf4j
public class TumblingWindow<S extends WindowState> {
    private final String name;

    @Getter
    private final WindowConfig<S> config;

    private final AtomicReference<S> current;

    public TumblingWindow(long startNs, String name, WindowConfig config) {
        this.config = config;
        this.name = name;
        this.current = new AtomicReference<>((S) config.newWindowState(startNs));
        log.info("[{}] created with {}", name, config);
    }

    public S current() {
        return current.get();
    }

    public void advance(WorkloadPriority priority) {
        S currentWindow = current();
        currentWindow.sample(priority);
        if (config.getRolloverStrategy().shouldRollover(currentWindow, 0, config)) {
            trySwapWindow(0, currentWindow);
        }
    }

    @ThreadSafe
    public void advance(WorkloadPriority priority, boolean admitted, long nowNs) {
        S currentWindow = current();
        currentWindow.sample(priority, admitted);
        if (config.getRolloverStrategy().shouldRollover(currentWindow, nowNs, config)) {
            trySwapWindow(nowNs, currentWindow);
        }
    }

    @ThreadSafe
    private void trySwapWindow(long nowNs, S currentWindow) {
        if (!currentWindow.tryAcquireSwappingLock()) {
            // offers an early exit to avoid unnecessary preparation for the swap
            return;
        }

        S nextWindow = (S) config.newWindowState(nowNs);
        if (current.compareAndSet(currentWindow, nextWindow)) { // TODO set
            // 此后，采样数据都进入新窗口，currentWindow 内部状态不会再变化
            if (log.isDebugEnabled()) {
                // 日志的输出顺序可能与实际的窗口切换顺序不一致
                // 窗口切换的顺序：a -> b -> c，但日志可能是：b -> c, a -> b
                // ThreadA，完成切换：a -> b，但在日志输出前被阻塞或者调度另一个线程
                // ThreadB，完成切换：b -> c，并且输出了日志
                // ThreadA被调度，输出日志
                // 如果在compareAndSet前面输出日志，那么该日志顺序与窗口切换顺序一定一致
                if (currentWindow instanceof TimeAndCountWindowState) {
                    TimeAndCountWindowState state = (TimeAndCountWindowState) currentWindow;
                    log.debug("[{}] after:{}ms, swapped window:{} -> {}, admitted:{}/{}, delta:{}",
                            name, state.ageMs(nowNs),
                            state.hashCode(), state.hashCode(),
                            state.admitted(), state.requested(),
                            state.requested() - config.getRequestCycle());
                }
                if (currentWindow instanceof CountWindowState) {
                    CountWindowState state = (CountWindowState) currentWindow;
                    log.debug("[{}] swapped window:{} -> {}, requested:{}, delta:{}",
                            name,
                            state.hashCode(), state.hashCode(),
                            state.requested(), state.requested() - config.getRequestCycle());
                }


            }

            // 此时的 currentWindow 是该窗口的最终值
            config.getOnWindowSwap().accept(nowNs, currentWindow);
            currentWindow.cleanup();
        } else {
            // should never happen
            log.error("Expected to swap the window but the compareAndSet operation failed.");
        }
    }

}
