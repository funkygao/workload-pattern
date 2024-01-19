package io.github.workload.metrics.smoother;

import io.github.workload.annotations.ThreadSafe;
import io.github.workload.annotations.VisibleForTesting;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * 简单移动平均(SMA)算法.
 *
 * <p>将最近N个数据点的平均值作为当前点的平滑值，可以去除短期波动，显示中期趋势</p>
 */
@ThreadSafe
class SimpleMovingAverage implements ValueSmoother {
    private static final long DOUBLE_LONG_SCALE = 1_000_000;

    // 保留最近多少条数据
    private final int windowSize;
    private final AtomicReferenceArray<Double> window;
    // 窗口了已经填充了多少个数据点
    private final AtomicInteger elements;
    // double scaled to long
    private final AtomicLong sum;
    private final AtomicInteger currentIndex;

    SimpleMovingAverage(int windowSize) {
        this.windowSize = windowSize;
        window = new AtomicReferenceArray<>(windowSize);
        sum = new AtomicLong(0);
        elements = new AtomicInteger(0);
        currentIndex = new AtomicInteger(0);
    }

    @Override
    public ValueSmoother update(double newValue) {
        final int newValueIndex = getAndIncrementOverflowSafe(currentIndex, 0) % windowSize;
        final Double evictedValue = window.getAndSet(newValueIndex, newValue);
        if (evictedValue != null) {
            // 出现了淘汰，说明窗口已经填满了
            sum.addAndGet(-((long) (evictedValue * DOUBLE_LONG_SCALE)));
        } else {
            elements.incrementAndGet();
        }

        final long scaledNewValue = (long) (newValue * DOUBLE_LONG_SCALE);
        sum.addAndGet(scaledNewValue);
        return this;
    }

    @Override
    public double smoothedValue() {
        final int n = elements.get();
        if (n == 0) {
            throw new IllegalStateException("MUST call update() before getting value!");
        }

        // the average based on the scaled sum
        return sum.get() / (double) (n * DOUBLE_LONG_SCALE);
    }

    @VisibleForTesting
    int getAndIncrementOverflowSafe(AtomicInteger atomicInteger, int resetValueWhenOverflow) {
        int current;
        do {
            current = atomicInteger.get();
            if (current == Integer.MAX_VALUE) {
                atomicInteger.compareAndSet(current, resetValueWhenOverflow);
            } else {
                atomicInteger.compareAndSet(current, current + 1);
            }
        } while (current == Integer.MAX_VALUE);
        return current;
    }
}
