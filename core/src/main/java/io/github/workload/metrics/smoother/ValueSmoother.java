package io.github.workload.metrics.smoother;

import io.github.workload.annotations.ThreadSafe;

/**
 * Value smoother to prevent phenomenon burrs.
 */
@ThreadSafe
public interface ValueSmoother {

    /**
     * 接收新的原始值.
     */
    ValueSmoother update(double newValue);

    /**
     * 平滑后的值.
     */
    double smoothedValue();

    /**
     * 创建一个指数移动平均(EMA)算法实现.
     *
     * @param alpha 近期数据权重，(0, 1]
     * @return a new instance
     */
    static ValueSmoother ofEMA(double alpha) {
        return new ExponentialMovingAverage(alpha);
    }
    
}
