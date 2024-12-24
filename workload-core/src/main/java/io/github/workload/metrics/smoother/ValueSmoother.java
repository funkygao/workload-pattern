package io.github.workload.metrics.smoother;

import io.github.workload.annotations.ThreadSafe;

/**
 * Value smoother to prevent phenomenon burrs.
 */
@ThreadSafe
public interface ValueSmoother {

    /**
     * 接收新的采样值.
     *
     * @param sample 最新的采样数据值
     */
    ValueSmoother update(double sample);

    /**
     * 平滑处理后的值.
     *
     * @return 取决于具体实现算法，返回多次采样数据平滑处理后的值
     */
    double smoothedValue();

    /**
     * 创建一个指数移动平均(EMA)算法实现.
     *
     * @param alpha 近期数据权重，(0, 1]
     */
    static ValueSmoother ofEMA(double alpha) {
        return new ExponentialMovingAverage(alpha);
    }

    /**
     * 创建一个简单移动平均(SMA)算法实现.
     *
     * @param windowSize 窗口大小，即保留最近多少条数据
     */
    static ValueSmoother ofSMA(int windowSize) {
        return new SimpleMovingAverage(windowSize);
    }

    /**
     * 创建一个移动平均算法实现.
     *
     * @param beta the decay factor
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
