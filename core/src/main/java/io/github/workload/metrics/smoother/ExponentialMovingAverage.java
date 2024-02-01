package io.github.workload.metrics.smoother;

import io.github.workload.annotations.ThreadSafe;

/**
 * 指数移动平均(EMA)算法.
 */
@ThreadSafe
class ExponentialMovingAverage implements ValueSmoother {

    /**
     * 平滑系数，用于控制对最近数据变化的敏感度，即：近期数据权重.
     *
     * <p>alpha值越大，新数据对EMA的影响越大(对近期数据更敏感)，平滑度越低.</p>
     * <p>0 < alpha < 1</p>
     */
    private final double alpha;
    private volatile Double curr;

    ExponentialMovingAverage(double alpha) {
        if (alpha <= 0 || alpha > 1) {
            throw new IllegalArgumentException("Alpha must be between 0 and 1");
        }

        this.alpha = alpha;
        this.curr = null;
    }

    @Override
    public ExponentialMovingAverage update(double sample) {
        if (curr == null) {
            curr = sample;
        } else {
            curr = alpha * sample + (1 - alpha) * curr;
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
        if (curr == null) {
            throw new IllegalStateException("MUST call update() before getting value!");
        }

        return curr;
    }

}
