package io.github.workload.overloading;

import io.github.workload.annotations.Immutable;
import io.github.workload.annotations.VisibleForTesting;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

@AllArgsConstructor
@Getter(AccessLevel.PACKAGE)
@Immutable
class WindowConfig {
    static final long NS_PER_MS = TimeUnit.MILLISECONDS.toNanos(1);

    @VisibleForTesting
    static final long DEFAULT_TIME_CYCLE_NS = System.getProperty("workload.window.DEFAULT_TIME_CYCLE_MS") != null ?
            TimeUnit.MILLISECONDS.toNanos(Long.valueOf(System.getProperty("workload.window.DEFAULT_TIME_CYCLE_MS"))) :
            TimeUnit.MILLISECONDS.toNanos(1000); // 1s

    @VisibleForTesting
    static final int DEFAULT_REQUEST_CYCLE = System.getProperty("workload.window.DEFAULT_REQUEST_CYCLE") != null ?
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

    private final BiConsumer<Long, WindowState> onWindowSwap;

    WindowConfig(BiConsumer<Long, WindowState> onWindowSwap) {
        this(DEFAULT_TIME_CYCLE_NS, DEFAULT_REQUEST_CYCLE, onWindowSwap);
    }

    @Override
    public String toString() {
        return "WindowConfig(time=" + timeCycleNs / NS_PER_MS / 1000 + "s,count=" + requestCycle + ")";
    }
}
