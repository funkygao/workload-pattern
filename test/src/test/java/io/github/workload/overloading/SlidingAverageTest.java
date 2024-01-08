package io.github.workload.overloading;

import io.github.workload.BaseConcurrentTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SlidingAverageTest extends BaseConcurrentTest {

    @Test
    void invalidCall() {
        SlidingAverage sa = new SlidingAverage(0.2);
        try {
            sa.smoothedValue();
            fail();
        } catch (IllegalStateException expected) {
        }

        sa.update(0.3);
        sa.smoothedValue();

        try {
            new SlidingAverage(-1);
            fail();
        } catch (IllegalArgumentException expected) {

        }

        try {
            new SlidingAverage(1.1);
            fail();
        } catch (IllegalArgumentException expected) {

        }

        try {
            new SlidingAverage(1);
            fail();
        } catch (IllegalArgumentException expected) {

        }
    }

    @Test
    void evaluateAlphaSmoothness() {
        double[] betas = new double[]{0.1d, 0.2d, 0.3d, 0.4d, 0.5d, 0.6d, 0.7d, 0.8d, 0.9d};
        for (double beta : betas) {
            ValueSmoother valueSmoother = new SlidingAverage(beta);
            double[] cpuUsages = new double[]{0.1d, 0.1d, 0.8d, 0.9d, 0.2d, 0.1d, 0.99d, 0.5d,
                    0.8d, 0.8d, 0.9d, 0.95d};
            for (double cpuUsage : cpuUsages) {
                valueSmoother.update(cpuUsage);
                log.info("beta:{}, cpu usage:{}, value:{}", beta, cpuUsage, valueSmoother.smoothedValue());
            }
        }
    }

    @Test
    void beta_is_0() {
        SlidingAverage sa = new SlidingAverage(0);
        sa.update(0.1);
        assertEquals(0.1, sa.smoothedValue(), DELTA);
        sa.update(0.95);
        assertEquals(0.95, sa.smoothedValue(), DELTA);
        sa.update(1.934);
        assertEquals(1.934, sa.smoothedValue(), DELTA);
    }

    @Test
    void beta_is_09() {
        SlidingAverage sa = new SlidingAverage(0.9);
        sa.update(0.1);
        for (int i = 0; i < 10; i++) {
            sa.update(0.1 + 0.01 * i);
        }
        System.out.println(sa.smoothedValue());


    }

}