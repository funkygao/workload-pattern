package io.github.workload.metrics.smoother;

import io.github.workload.annotations.ThreadSafe;

/**
 * 简单移动平均算法.
 */
@ThreadSafe
class SlidingAverage implements ValueSmoother {

    /**
     * Vᵗ = β⋅Vᵗ⁻¹ + (1-β)⋅θᵗ.
     *
     * <ul>beta, the decay factor
     * <li>0: Vt = θt，相当于没有使用移动平均</li>
     * <li>0.9: Vt is approximately the average of the last 10 θt values</li>
     * <li>0.99: Vt is approximately the average of the last 100 θt values</li>
     * </ul>
     */
    private final double beta;
    private volatile Double sa;

    public SlidingAverage(double beta) {
        if (beta < 0 || beta >= 1) {
            throw new IllegalArgumentException("Beta must be between 0 and 1");
        }
        this.beta = beta;
        this.sa = null;
    }

    @Override
    public SlidingAverage update(double newValue) {
        if (sa == null) {
            sa = newValue;
        } else {
            sa = beta * sa + (1 - beta) * newValue;
        }
        return this;
    }

    /**
     * 指数移动平均值.
     *
     * @throws IllegalStateException must call {@link #update(double)} before call {@link #smoothedValue()}
     */
    @Override
    public double smoothedValue() throws IllegalStateException {
        if (sa == null) {
            throw new IllegalStateException("MUST call update() before getting value!");
        }

        return sa;
    }

}
