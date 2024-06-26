package io.github.workload.metrics.smoother;

import java.util.concurrent.atomic.AtomicReference;

/**
 * 简单移动平均算法.
 */
class SlidingAverage implements ValueSmoother {

    /**
     * Vᵗ = β⋅Vᵗ⁻¹ + (1-β)⋅θᵗ.
     *
     * <ul>beta, the decay factor：越小代表最新一个采样值越重要
     * <li>0: Vt = θt，相当于没有使用移动平均</li>
     * <li>0.9: Vt is approximately the average of the last 10 θt values</li>
     * <li>0.99: Vt is approximately the average of the last 100 θt values</li>
     * </ul>
     */
    private final double beta;

    private final AtomicReference<Double> curr;

    SlidingAverage(double beta) {
        if (beta < 0 || beta >= 1) {
            throw new IllegalArgumentException("Beta must be between 0 and 1");
        }

        this.beta = beta;
        this.curr = new AtomicReference<>(null);
    }

    @Override
    public SlidingAverage update(double sample) {
        curr.updateAndGet(current -> current == null ? sample : beta * current + (1 - beta) * sample);
        return this;
    }

    /**
     * 指数移动平均值.
     *
     * @throws IllegalStateException must call {@link #update(double)} before call {@link #smoothedValue()}
     */
    @Override
    public double smoothedValue() throws IllegalStateException {
        Double current = curr.get();
        if (current == null) {
            throw new IllegalStateException("MUST call update() before getting value!");
        }

        return current;
    }

}
