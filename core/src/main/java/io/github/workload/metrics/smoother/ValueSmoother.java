package io.github.workload.metrics.smoother;

import io.github.workload.annotations.ThreadSafe;

/**
 * Value smoother to prevent phenomenon burrs.
 */
@ThreadSafe
public interface ValueSmoother {

    /**
     * 接收新的采用值.
     */
    ValueSmoother update(double sample);

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

    /**
     * 创建一个简单移动平均(SMA)算法实现.
     *
     * @param windowSize 窗口大小，即保留最近多少条数据
     * @return a new instance
     */
    static ValueSmoother ofSMA(int windowSize) {
        return new SimpleMovingAverage(windowSize);
    }

    /**
     * 创建一个移动平均算法实现.
     *
     * @param beta the decay factor
     * @return a new instance
     */
    static ValueSmoother ofSA(double beta) {
        return new SlidingAverage(beta);
    }

    /**
     * 创建一个不做数据平滑处理的实现.
     */
    static ValueSmoother ofLatestValue() {
        return new LatestValue();
    }

}
