package io.github.workload.metrics.smoother;

import io.github.workload.BaseConcurrentTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExponentialMovingAverageTest extends BaseConcurrentTest {

    @Test
    void invalidCall() {
        double[] invalidAlphas = new double[]{0, -0.1, 1.1};
        for (double invalidAlpha : invalidAlphas) {
            assertThrows(IllegalArgumentException.class, () -> {
                new ExponentialMovingAverage(invalidAlpha);
            });
        }

        // 1 is ok
        new ExponentialMovingAverage(1d);
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
    void getValueBeforeUpdate() {
        final ExponentialMovingAverage ema = new ExponentialMovingAverage(0.4);
        Exception expected = assertThrows(IllegalStateException.class, () -> {
            ema.smoothedValue();
        });
        assertEquals("MUST call update() before getting value!", expected.getMessage());
    }

    @Test
    void basic() {
        ExponentialMovingAverage ema = new ExponentialMovingAverage(0.4);
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

    @DisplayName("alpha=1，相当于没有平滑，只反映最近1次数据")
    @Test
    void alpha_is_1() {
        ValueSmoother smoother = new ExponentialMovingAverage(1d);
        double value = 5.0d;
        smoother.update(value);
        assertEquals(value, smoother.smoothedValue(), DELTA);
        value = 2.12;
        smoother.update(value);
        assertEquals(value, smoother.smoothedValue(), DELTA);
    }

    @DisplayName("alpha=0.9，最近数据权重更大")
    @Test
    void alpha_is_09() {
        ValueSmoother smoother = new ExponentialMovingAverage(0.9d);
        double[] values = new double[]{0.1, 0.2, 0.15, 0.3, 0.05, 0.8, 0.85};
        for (double value : values) {
            smoother.update(value);
        }
        assertEquals(0.8377354, smoother.smoothedValue(), DELTA);

        smoother.update(3.2);
        assertEquals(2.96377354, smoother.smoothedValue(), DELTA);

        smoother.update(0.1);
        assertEquals(0.386377354, smoother.smoothedValue(), DELTA);
    }

    @DisplayName("alpha=0.5")
    @Test
    void alpha_is_05() {
        ValueSmoother smoother = new ExponentialMovingAverage(0.5d);
        double[] values = new double[]{0.1, 0.2, 0.15, 0.3, 0.05, 0.8, 0.85};
        for (double value : values) {
            smoother.update(value);
        }
        assertEquals(0.659375, smoother.smoothedValue(), DELTA);

        smoother.update(3.2);
        assertEquals(1.9296875, smoother.smoothedValue(), DELTA);

        smoother.update(0.1);
        assertEquals(1.01484375, smoother.smoothedValue(), DELTA);
    }

}