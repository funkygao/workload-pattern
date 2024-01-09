package io.github.workload.metrics;

/**
 * Wrapper entity class for a period of time window.
 *
 * @param <StatisticData> Statistic data type
 */
public class WindowBucket<StatisticData> {
    private final long bucketLengthInMs;
    private long startMillis;
    private StatisticData data;

    public WindowBucket(long bucketLengthInMs, long startMillis, StatisticData data) {
        this.bucketLengthInMs = bucketLengthInMs;
        this.startMillis = startMillis;
        this.data = data;
    }

    public long startMillis() {
        return startMillis;
    }

    public StatisticData data() {
        return data;
    }

    WindowBucket<StatisticData> resetStartTimeMillis(long startTimeMillis) {
        this.startMillis = startTimeMillis;
        return this;
    }

    boolean isTimeInBucket(long timeMillis) {
        return startMillis <= timeMillis
                && timeMillis < startMillis + bucketLengthInMs;
    }

    @Override
    public String toString() {
        return "WindowBucket{" +
                "bucketLengthInMs=" + bucketLengthInMs +
                ", windowStart=" + startMillis +
                ", value=" + data +
                '}';
    }
}
