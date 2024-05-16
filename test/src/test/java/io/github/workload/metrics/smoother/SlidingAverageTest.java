package io.github.workload.metrics.smoother;

import io.github.workload.BaseTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SlidingAverageTest extends BaseTest {

    @Test
    void invalidCall() {
        SlidingAverage sa = new SlidingAverage(0.2);
        // update before you get smoothed value
        assertThrows(IllegalStateException.class, () -> {
            sa.smoothedValue();
        });

        sa.update(0.3);
        sa.smoothedValue();

        double[] invalidBetas = new double[]{-1, 1.1, 1};
        for (double beta : invalidBetas) {
            assertThrows(IllegalArgumentException.class, () -> {
                new SlidingAverage(beta);
            });
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

    @Test
    void simulateKafkaAdaptiveUrgentPartitioner() {
        ValueSmoother smoother = ValueSmoother.ofSA(0.9999);
        double expectedUrgentPercentage = 0.2d;
        double actualUrgentPercentage = 0d;
        for (int i = 0; i < 1 << 20; i++) {
            actualUrgentPercentage += 0.001;
            final double smoothedActual = smoother.update(actualUrgentPercentage).smoothedValue();
            final double errorRate = (smoothedActual - expectedUrgentPercentage) / expectedUrgentPercentage;
            if (errorRate > 0.5) {
                final double newExpected = expectedUrgentPercentage * 1.1;
                log.info("{} expected:{}, actual:{}, err:{} 期望值调整 -> {}", i, expectedUrgentPercentage, smoothedActual, errorRate, newExpected);
                expectedUrgentPercentage = newExpected;
            }
        }
    }

}