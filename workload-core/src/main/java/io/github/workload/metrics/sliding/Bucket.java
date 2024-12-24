package io.github.workload.metrics.sliding;

/**
 * Wrapper entity class for a period of time window.
 *
 * @param <StatisticData> Statistic data type
 */
public class Bucket<StatisticData> {
    private final long durationMs;
    private long startMillis; // [startMillis, endMillis=startMillis+durationMs)
    private final StatisticData data;

    Bucket(long durationMs, long startMillis, StatisticData data) {
        this.durationMs = durationMs;
        this.startMillis = startMillis;
        this.data = data;
    }

    public long startMillis() {
        return startMillis;
    }

    public StatisticData data() {
        return data;
    }

    void resetStartTimeMillis(long startTimeMillis) {
        this.startMillis = startTimeMillis;
    }

    boolean isTimeInBucket(long timeMillis) {
        return startMillis <= timeMillis
                && timeMillis < startMillis + durationMs;
    }

    @Override
    public String toString() {
        return "WindowBucket(" +
                "durationMs=" + durationMs +
                ", windowStart=" + startMillis +
                ')';
    }
}
