package io.github.workload.overloading.detect;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class KernelDensityEstimatorTest {

    @Test
    void cpu_abnormality_detection() {
        double[] normalCpuUsage = {10, 15, 12, 11, 13, 14, 12, 11, 15, 14};
        double bandwidth = 2.0;
        KernelDensityEstimator kde = new KernelDensityEstimator(normalCpuUsage, bandwidth);
        double newCpuUsage = 25.0;
        double threshold = 0.01; // 这个值需要根据实际情况调整
        assertTrue(kde.isAnomaly(newCpuUsage, threshold));
    }

}