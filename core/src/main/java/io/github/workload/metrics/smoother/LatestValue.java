package io.github.workload.metrics.smoother;

/**
 * A class implementing ValueSmoother that simply returns the latest value without any smoothing.
 */
class LatestValue implements ValueSmoother {
    private volatile double latestValue;

    @Override
    public ValueSmoother update(double sample) {
        this.latestValue = sample;
        return this;
    }

    @Override
    public double smoothedValue() {
        return latestValue;
    }
}
