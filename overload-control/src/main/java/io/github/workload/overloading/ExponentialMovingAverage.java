package io.github.workload.overloading;

import io.github.workload.annotations.ThreadSafe;

/**
 * 指数移动平均(EMA)算法.
 */
@ThreadSafe
class ExponentialMovingAverage implements ValueSmoother {
    /**
     * 平滑系数，用于控制对最近数据变化的敏感度.
     *
     * <p>alpha值越大，新数据对EMA的影响越大，平滑度越低.</p>
     */
    private final double alpha;
    private volatile Double ema;

    ExponentialMovingAverage(double alpha) {
        if (alpha <= 0 || alpha > 1) {
            throw new IllegalArgumentException("Alpha must be between 0 and 1");
        }

        this.alpha = alpha;
        this.ema = null;
    }

    @Override
    public ValueSmoother update(double newValue) {
        if (ema == null) {
            ema = newValue;
        } else {
            ema = alpha * newValue + (1 - alpha) * ema;
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
        if (ema == null) {
            throw new IllegalStateException("MUST call update() before getting value!");
        }

        return ema;
    }

}
