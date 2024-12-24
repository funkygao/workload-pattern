package io.github.workload.metrics.noise;

import io.github.workload.annotations.NotThreadSafe;

/**
 * A utility class to perform Z-score denoising on a dataset.
 */
@NotThreadSafe
public class ZScoreDenoiser {
    private final double[] window;

    private double sum;
    private double avg;

    private int collectedCount; // 总计采集了多少条数据
    private int idx; // window current index

    public ZScoreDenoiser(int windowSize) {
        this.window = new double[windowSize];
    }

    private void updateStatistics(double newValue) {
        int len = this.window.length;
        this.idx = this.collectedCount++ % len;
        double oldRecord = this.window[this.idx];
        this.window[this.idx] = newValue;
        if (this.collectedCount < len) {
            this.sum += newValue;
            this.avg = this.sum / (double)this.collectedCount;
        } else {
            this.sum = this.sum + newValue - oldRecord;
            this.avg = this.sum / (double)len;
        }
    }

    private double standardDeviation() {
        int len = Math.min(this.collectedCount, this.window.length);
        double variance = 0.0;

        for(int i = 0; i < len; ++i) {
            variance += Math.pow(this.window[i] - this.avg, 2.0);
        }

        return Math.sqrt(variance / (double)len);
    }

    /**
     * 对给定的数据点应用去噪过程。
     *
     * @param newValue 当前要处理的数据点。
     * @param zScoreThreshold Z分数的阈值。如果数据点的Z分数绝对值小于这个阈值，该数据点被认为是有效的，否则被视为噪声。
     * @return 如果当前数据点被认为是有效的，则返回该数据点；如果被认为是噪声，则返回滑动窗口内有效数据点的平均值。
     */
    public double denoise(double newValue, double zScoreThreshold) {
        this.updateStatistics(newValue);

        double sd = this.standardDeviation();
        if (sd == 0) {
            return newValue;
        }

        int len = Math.min(this.collectedCount, this.window.length);
        double sum = 0.0;
        int count = 0;
        for(int i = 0; i < len; ++i) {
            double zScore = (this.window[i] - this.avg) / sd;
            if (Math.abs(zScore) < zScoreThreshold) {
                if (i == this.idx) {
                    return newValue;
                }

                sum += this.window[i];
                ++count;
            }
        }

        return count == 0 ? newValue : sum / (double)count;
    }

    /**
     * 对给定的数据点应用单侧去噪过程。
     * <p>
     * 此方法类似于 {@link #denoise(double, double)}，但它仅考虑Z分数小于给定阈值的数据点。
     * 这意味着只有当数据点相对于滑动窗口内的平均值偏低且低于指定的Z分数阈值时，才将其视为有效数据。
     * 如果当前数据点的Z分数低于阈值，该方法将直接返回当前数据点；否则，它将返回滑动窗口内符合条件的数据点的平均值。
     * </p>
     *
     * @param newValue         当前要处理的数据点。
     * @param zScoreThreshold  Z分数的阈值。仅当数据点的Z分数小于这个阈值时，该数据点被认为是有效的。
     * @return                 如果当前数据点的Z分数小于阈值，则直接返回该数据点；
     *                         否则，返回滑动窗口内符合条件的数据点的平均值。
     *                         如果滑动窗口内没有任何数据点的Z分数小于阈值，将返回当前数据点。
     */
    public double denoiseRight(double newValue, double zScoreThreshold) {
        this.updateStatistics(newValue);

        double sd = this.standardDeviation();
        if (sd == 0) {
            return newValue;
        }

        int len = Math.min(this.collectedCount, this.window.length);
        double sum = 0.0;
        int count = 0;
        for(int i = 0; i < len; ++i) {
            double zScore = (this.window[i] - this.avg) / sd;
            if (zScore < zScoreThreshold) {
                if (i == this.idx) {
                    return newValue;
                }

                sum += this.window[i];
                ++count;
            }
        }

        return count == 0 ? newValue : sum / (double)count;
    }

}
