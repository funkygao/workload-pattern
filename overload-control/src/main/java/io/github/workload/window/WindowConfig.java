package io.github.workload.window;

import io.github.workload.annotations.Immutable;
import io.github.workload.annotations.VisibleForTesting;
import io.github.workload.overloading.WorkloadPriority;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Function;

@AllArgsConstructor
@Getter
@Immutable
public class WindowConfig {
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

    private final BiConsumer<Long, WindowState> onWindowSwap;

    private final Function<WorkloadPriority, Integer> histogramKeyer;

    public WindowConfig(WindowRolloverStrategy rolloverStrategy, BiConsumer<Long, WindowState> onWindowSwap, Function<WorkloadPriority, Integer> histogramKeyer) {
        this(DEFAULT_TIME_CYCLE_NS, DEFAULT_REQUEST_CYCLE, rolloverStrategy, onWindowSwap, histogramKeyer);
    }

    @Override
    public String toString() {
        return "WindowConfig(time=" + timeCycleNs / NS_PER_MS / 1000 + "s,count=" + requestCycle + ")";
    }
}
