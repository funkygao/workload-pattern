package io.github.workload.aqm;

import io.github.workload.annotations.PoC;

/**
 * The additive-increase/multiplicative-decrease (AIMD) algorithm.
 *
 * @see <a href="https://en.wikipedia.org/wiki/Additive_increase/multiplicative_decrease">wikipedia</a>
 */
@PoC
class AIMD {
    private volatile double windowSize; // 控制变量，比如TCP的拥塞窗口
    private final double alpha; // 加性增长因子
    private final double beta; // 乘性减少因子

    public AIMD(double initialWindow, double alpha, double beta) {
        this.windowSize = initialWindow;
        this.alpha = alpha;
        this.beta = beta;
    }

    // 当网络表现良好时调用这个方法
    public void additiveIncrease() {
        windowSize += alpha;
    }

    // 当网络拥塞时调用这个方法
    public void multiplicativeDecrease() {
        windowSize *= beta;
    }

    public double windowSize() {
        return windowSize;
    }

}
