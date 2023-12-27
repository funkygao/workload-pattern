package io.github.workload.window;

import io.github.workload.annotations.Immutable;
import io.github.workload.annotations.VisibleForTesting;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;

import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/**
 * 滚动窗口的配置.
 *
 * @param <S> 具体的窗口状态
 */
@AllArgsConstructor
@Getter
@Immutable
public class WindowConfig<S extends WindowState> {
    static final long NS_PER_MS = TimeUnit.MILLISECONDS.toNanos(1);

    private static final long DEFAULT_TIME_CYCLE_NS = System.getProperty("workload.window.DEFAULT_TIME_CYCLE_MS") != null ?
            TimeUnit.MILLISECONDS.toNanos(Long.valueOf(System.getProperty("workload.window.DEFAULT_TIME_CYCLE_MS"))) :
            TimeUnit.MILLISECONDS.toNanos(1000); // 1s

    @VisibleForTesting
    public static final int DEFAULT_REQUEST_CYCLE = System.getProperty("workload.window.DEFAULT_REQUEST_CYCLE") != null ?
            Integer.valueOf(System.getProperty("workload.window.DEFAULT_REQUEST_CYCLE")) :
            2 << 10; // 2K

    /**
     * 时间周期.
     */
    private final long timeCycleNs;

    /**
     * 请求数量周期.
     */
    private final int requestCycle;

    private final WindowRolloverStrategy rolloverStrategy;

    private final BiConsumer<Long, S> onWindowSwap;


    public WindowConfig(@NonNull WindowRolloverStrategy rolloverStrategy, @NonNull BiConsumer<Long, S> onWindowSwap) {
        this(DEFAULT_TIME_CYCLE_NS, DEFAULT_REQUEST_CYCLE, rolloverStrategy, onWindowSwap);
    }

    @Override
    public String toString() {
        return "WindowConfig(time=" + timeCycleNs / NS_PER_MS / 1000 + "s,count=" + requestCycle + ")";
    }

    WindowState newWindowState(long nowNs) {
        if (rolloverStrategy instanceof CountRolloverStrategy) {
            return new CountWindowState();
        } else {
            return new TimeAndCountWindowState(nowNs);
        }
    }

}
