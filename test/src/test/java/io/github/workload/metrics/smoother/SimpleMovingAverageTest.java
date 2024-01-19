package io.github.workload.metrics.smoother;

import io.github.workload.BaseConcurrentTest;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class SimpleMovingAverageTest extends BaseConcurrentTest {

    @Test
    void badCase() {
        ValueSmoother smoother = ValueSmoother.ofSMA(2);
        try {
            smoother.smoothedValue();
            fail();
        } catch (IllegalStateException expected) {

        }
    }

    @Test
    void atomicIntegerOverflow() {
        AtomicInteger n = new AtomicInteger(Integer.MAX_VALUE);
        n.getAndIncrement();
        assertEquals(Integer.MIN_VALUE, n.get());

        SimpleMovingAverage sma = new SimpleMovingAverage(2);
        n = new AtomicInteger(Integer.MAX_VALUE);
        assertEquals(0, sma.getAndIncrementOverflowSafe(n, 0));
    }

    @Test
    void basic() {
        ValueSmoother smoother = ValueSmoother.ofSMA(3);
        double[] values = new double[]{0.1, 0.2, 0.15, 0.3, 0.05, 0.8, 0.85, 0.6, 0.4, 0.1};
        for (double value : values) {
            smoother.update(value);
            log.info("{} smoothed:{}", value, smoother.smoothedValue());
        }
    }

}