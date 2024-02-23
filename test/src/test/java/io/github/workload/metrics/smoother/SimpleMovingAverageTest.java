package io.github.workload.metrics.smoother;

import io.github.workload.BaseConcurrentTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class SimpleMovingAverageTest extends BaseConcurrentTest {

    @Test
    void badCase() {
        ValueSmoother smoother = ValueSmoother.ofSMA(2);
        assertThrows(IllegalStateException.class, () -> {
            smoother.smoothedValue();
        });
        Exception expected = assertThrows(IllegalArgumentException.class, () -> {
            new SimpleMovingAverage(0);
        });
        assertEquals("windowSize must > 0", expected.getMessage());
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
    void smoothedValueCanBeCalledManyTimes() {
        ValueSmoother smoother = ValueSmoother.ofSMA(2);
        smoother.update(1.0);
        smoother.update(2.3);
        smoother.update(1.89);
        assertEquals(smoother.smoothedValue(), smoother.smoothedValue());
    }

    @DisplayName("windowSize=1，相当于不平滑了，每次只取最近那个值")
    @Test
    void windowSize_is_1() {
        SimpleMovingAverage sma = new SimpleMovingAverage(1);
        double[] values = new double[]{0.1, 0.2, 0.15, 0.3, 0.05, 0.8, 0.85, 0.6, 0.4, 0.1};
        for (double value : values) {
            assertEquals(value, sma.update(value).smoothedValue());
        }
    }

    @Test
    void basic() {
        ValueSmoother smoother = ValueSmoother.ofSMA(3);
        double[] values = new double[]{0.1, 0.2, 0.15, 0.3, 0.05, 0.8, 0.85, 0.6, 0.4, 0.1};
        double[] expected = new double[]{0.1, 0.15, 0.15, 0.21666666666666667, 0.16666666666666666, 0.38333333333333336, 0.5666666666666667, 0.75, 0.6166666666666667, 0.36666666666666664};
        for (int i = 0; i < values.length; i++) {
            smoother.update(values[i]);
            assertEquals(expected[i], smoother.smoothedValue(), DELTA);
        }
    }

}