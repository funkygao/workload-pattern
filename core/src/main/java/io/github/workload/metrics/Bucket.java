package io.github.workload.metrics;

/**
 * Wrapper entity class for a period of time window.
 *
 * @param <StatisticData> Statistic data type
 */
public class Bucket<StatisticData> {
    private final long durationMs;
    private long startMillis; // [startMillis, endMillis=startMillis+durationMs)
    private StatisticData data;

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

    Bucket<StatisticData> resetStartTimeMillis(long startTimeMillis) {
        this.startMillis = startTimeMillis;
        return this;
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
