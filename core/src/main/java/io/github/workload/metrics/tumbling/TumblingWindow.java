package io.github.workload.metrics.tumbling;

import io.github.workload.annotations.ThreadSafe;
import io.github.workload.annotations.VisibleForTesting;
import io.github.workload.WorkloadPriority;
import lombok.Generated;
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
    @Generated
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
        currentWindow.logRollover(name, nowNs, nextWindow, config); // 打日志
        current.set(nextWindow); // 没必要CAS；此后，采样数据都进入新窗口，currentWindow 内部状态不会再变化
        config.getRolloverStrategy().onRollover(nowNs, currentWindow, this);
        currentWindow.cleanup(); // TODO async?
    }

}
