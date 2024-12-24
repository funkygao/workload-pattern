package io.github.workload.detect;

import io.github.workload.annotations.Heuristics;
import io.github.workload.annotations.PoC;

/**
 * Simplified prototype KDE for time series based abnormality detection.
 */
@PoC
class KernelDensityEstimator {

    @Heuristics
    private final double bandwidth; // 带宽，决定了平滑程度

    private final double[] data;

    KernelDensityEstimator(double[] data, double bandwidth) {
        this.data = data;
        this.bandwidth = bandwidth;
    }

    private double gaussianKernel(double x) {
        return (1 / Math.sqrt(2 * Math.PI)) * Math.exp(-0.5 * x * x);
    }

    private double estimateDensity(double x) {
        double sum = 0.0;
        for (double xi : data) {
            double scaled = (x - xi) / bandwidth;
            sum += gaussianKernel(scaled);
        }
        return sum / (data.length * bandwidth);
    }

    public boolean isAnomaly(double x, double threshold) {
        double density = estimateDensity(x);
        return density < threshold;
    }

}
