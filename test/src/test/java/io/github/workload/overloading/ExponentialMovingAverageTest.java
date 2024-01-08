package io.github.workload.overloading;

import io.github.workload.BaseConcurrentTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExponentialMovingAverageTest extends BaseConcurrentTest {

    @Test
    void invalidCall() {
        try {
            new ExponentialMovingAverage(0);
            fail();
        } catch (IllegalArgumentException expected) {
        }

        try {
            new ExponentialMovingAverage(-0.1);
            fail();
        } catch (IllegalArgumentException expected) {
        }

        try {
            new ExponentialMovingAverage(1.1);
            fail();
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    void evaluateAlphaSmoothness() {
        double[] alphas = new double[]{0.1d, 0.2d, 0.3d, 0.4d, 0.5d};
        for (double alpha : alphas) {
            ExponentialMovingAverage ema = new ExponentialMovingAverage(alpha);
            double[] cpuUsages = new double[]{0.1d, 0.1d, 0.8d, 0.9d, 0.2d, 0.1d, 0.99d, 0.5d,
                    0.8d, 0.8d, 0.9d, 0.95d};
            for (double cpuUsage : cpuUsages) {
                ema.update(cpuUsage);
                log.info("alpha:{}, cpu usage:{}, ema:{}", alpha, cpuUsage, ema.smoothedValue());
            }
        }
    }

    @Test
    void basic() {
        ExponentialMovingAverage ema = new ExponentialMovingAverage(0.4);
        try {
            ema.smoothedValue();
            fail();
        } catch (IllegalStateException expected) {
            assertEquals("MUST call update() before getting value!", expected.getMessage());
        }
        ema.update(0.5);
        assertEquals(0.5, ema.smoothedValue(), DELTA);
        ema = new ExponentialMovingAverage(0.4);
        ema.update(0.12);
        assertEquals(0.12, ema.smoothedValue(), DELTA);
        // 每次更新的值都等于种子值，则ema就是种子值
        for (int i = 0; i < 5; i++) {
            ema.update(0.12);
            assertEquals(0.12, ema.smoothedValue(), DELTA);
        }
        ema.update(0.2);
        assertTrue(ema.smoothedValue() > 0.12 && ema.smoothedValue() < 0.2);
    }

}