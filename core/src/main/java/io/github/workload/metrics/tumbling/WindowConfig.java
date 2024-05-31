package io.github.workload.metrics.tumbling;

import io.github.workload.HyperParameter;
import io.github.workload.annotations.Immutable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 滚动窗口的配置.
 *
 * @param <S> 具体的窗口状态
 */
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Immutable
@Getter(AccessLevel.PACKAGE)
@Slf4j
public class WindowConfig<S extends WindowState> {
    public static final long NS_PER_MS = TimeUnit.MILLISECONDS.toNanos(1);
    public static final long DEFAULT_TIME_CYCLE_NS = TimeUnit.MILLISECONDS.toNanos(HyperParameter.getLong(HyperParameter.WINDOW_TIME_CYCLE_MS, 1000)); // 1s
    public static final int DEFAULT_REQUEST_CYCLE = HyperParameter.getInt(HyperParameter.WINDOW_REQUEST_CYCLE, 1 << 10);

    static final long MIN_TIME_CYCLE_NS = DEFAULT_TIME_CYCLE_NS / 5;
    static final long MAX_TIME_CYCLE_NS = DEFAULT_TIME_CYCLE_NS * 2;

    /**
     * 时间周期.
     */
    @Getter
    private final AtomicLong timeCycleNs;

    /**
     * 请求数量周期.
     */
    private final int requestCycle;

    private final WindowRolloverStrategy<S> rolloverStrategy;

    /**
     * 根据默认的(时间，数量)周期创建窗口配置.
     */
    public static <T extends WindowState> WindowConfig<T> create(@NonNull WindowRolloverStrategy<T> rolloverStrategy) {
        return create(DEFAULT_TIME_CYCLE_NS, DEFAULT_REQUEST_CYCLE, rolloverStrategy);
    }

    /**
     * 创建窗口配置.
     *
     * <p>编译器通常通过擦除机制允许未带泛型信息的类型存在，为了提高泛型的类型安全性，采用静态工厂方法解决</p>
     */
    public static <T extends WindowState> WindowConfig<T> create(long timeCycleNs, int requestCycle, @NonNull WindowRolloverStrategy<T> rolloverStrategy) {
        return new WindowConfig<>(new AtomicLong(timeCycleNs), requestCycle, rolloverStrategy);
    }

    @Override
    public String toString() {
        return "WindowConfig(time=" + timeCycleNs.get() / NS_PER_MS / 1000 + "s,count=" + requestCycle + ")";
    }

    /**
     * 窗口状态的工厂方法，切换窗口时切换到新的空窗口.
     */
    S createWindowState(long nowNs) {
        return rolloverStrategy.createWindowState(nowNs);
    }

    /**
     * 缩放时间窗口大小.
     *
     * @param factor 缩放因子，大于1表示放大，小于1则缩小
     */
    void zoomTimeCycle(double factor) {
        if (factor == 1) {
            return;
        }

        final double effectiveFactor = Math.max(0.2, Math.min(2, factor));
        timeCycleNs.updateAndGet(current -> {
            long newValue = (long) (current * effectiveFactor);
            newValue = Math.max(MIN_TIME_CYCLE_NS, Math.min(newValue, MAX_TIME_CYCLE_NS));
            log.info("timeCycleNs zoom factor {}: {} -> {}", factor, current, newValue);
            return newValue;
        });
    }

}
