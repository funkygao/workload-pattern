package io.github.workload.metrics.smoother;

import io.github.workload.BaseConcurrentTest;
import org.junit.jupiter.api.Test;

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
    void basic() {
        ValueSmoother smoother = ValueSmoother.ofSMA(3);
        double[] values = new double[]{0.1, 0.2, 0.15, 0.3, 0.05, 0.8, 0.85, 0.6, 0.4, 0.1};
        for (double value : values) {
            smoother.update(value);
            log.info("{} smoothed:{}", value, smoother.smoothedValue());
        }
    }

}