package io.github.workload.metrics;

/**
 * Wrapper entity class for a period of time window.
 *
 * @param <StatisticData> Statistic data type
 */
public class WindowBucket<StatisticData> {
    private final long lengthInMs;
    private long startMillis; // [startMillis, endMillis=startMillis+lengthInMs)
    private StatisticData data;

    WindowBucket(long lengthInMs, long startMillis, StatisticData data) {
        this.lengthInMs = lengthInMs;
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
                && timeMillis < startMillis + lengthInMs;
    }

    @Override
    public String toString() {
        return "WindowBucket(" +
                "lengthInMs=" + lengthInMs +
                ", windowStart=" + startMillis +
                ')';
    }
}
