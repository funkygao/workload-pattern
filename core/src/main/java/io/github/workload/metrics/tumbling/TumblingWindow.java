package io.github.workload.metrics.tumbling;

import io.github.workload.annotations.ThreadSafe;
import io.github.workload.annotations.VisibleForTesting;
import io.github.workload.WorkloadPriority;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicReference;

/**
 * 基于(时间周期，请求数量周期)的滚动窗口，用于对工作负荷采样.
 *
 * @param <S> 具体的窗口状态
 */
@Slf4j
@ThreadSafe
public class TumblingWindow<S extends WindowState> {
    @Getter
    private final String name;

    @Getter
    private final WindowConfig<S> config;

    /**
     * 当前窗口状态.
     */
    private final AtomicReference<S> current;

    public TumblingWindow(@NonNull WindowConfig<S> config, @NonNull String name, long startNs) {
        this.name = name;
        this.config = config;
        this.current = new AtomicReference<>(config.createWindowState(startNs));
        log.info("[{}] created with {}", name, config);
    }

    /**
     * 当前窗口状态.
     */
    public S current() {
        return current.get();
    }

    @VisibleForTesting
    public void resetForTesting() {
        current().resetForTesting();
    }

    /**
     * 采样工作负荷，推动窗口前进.
     */
    public void advance(WorkloadPriority priority) {
        advance(priority, true /* 不关心是否准入 */, 0 /* 不关心时间 */);
    }

    /**
     * 采样工作负荷，推动窗口前进.
     *
     * <p>除了工作负荷本身，还关心时间和是否准入.</p>
     */
    public void advance(WorkloadPriority priority, boolean admitted, long nowNs) {
        S currentWindow = current();
        currentWindow.sample(priority, admitted);
        if (config.getRolloverStrategy().shouldRollover(currentWindow, nowNs, config)) {
            tryRollover(nowNs, currentWindow);
        }
    }

    private void tryRollover(long nowNs, S currentWindow) {
        if (!currentWindow.tryAcquireRolloverLock()) {
            // offers an early exit to avoid unnecessary preparation for the swap
            return;
        }

        S nextWindow = config.createWindowState(nowNs);
        // current.compareAndSet is unnecessary
        current.set(nextWindow); // 此后，采样数据都进入新窗口，currentWindow 内部状态不会再变化
        if (log.isDebugEnabled()) {
            // 日志的输出顺序可能与实际的窗口切换顺序不一致
            // 窗口切换的顺序：a -> b -> c，但日志可能是：b -> c, a -> b
            // ThreadA，完成切换：a -> b，但在日志输出前被阻塞或者调度另一个线程
            // ThreadB，完成切换：b -> c，并且输出了日志
            // ThreadA被调度，输出日志
            // 如果在compareAndSet前面输出日志，那么该日志顺序与窗口切换顺序一定一致
            currentWindow.logRollover(name, nowNs, nextWindow, config);
        }

        // 此时的 currentWindow 是该窗口的最终值
        config.getRolloverStrategy().onRollover(nowNs, currentWindow, this);
        currentWindow.cleanup(); // TODO async?
    }

}
