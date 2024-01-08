package io.github.workload.metrics;

/**
 * Wrapper entity class for a period of time window.
 *
 * @param <T> Statistic data type
 */
public class WindowBucket<T> {
    private final long bucketLengthInMs;
    private long windowStartMillis;
    private T value;

    public WindowBucket(long bucketLengthInMs, long windowStartMillis, T value) {
        this.bucketLengthInMs = bucketLengthInMs;
        this.windowStartMillis = windowStartMillis;
        this.value = value;
    }

    public long windowStartMillis() {
        return windowStartMillis;
    }

    public T value() {
        return value;
    }

    public void setValue(T value) {
        this.value = value;
    }

    public WindowBucket<T> resetStartTimeMillis(long startTimeMillis) {
        this.windowStartMillis = startTimeMillis;
        return this;
    }

    public boolean isTimeInWindow(long timeMillis) {
        return windowStartMillis <= timeMillis
                && timeMillis < windowStartMillis + bucketLengthInMs;
    }

    @Override
    public String toString() {
        return "WindowBucket{" +
                "bucketLengthInMs=" + bucketLengthInMs +
                ", windowStart=" + windowStartMillis +
                ", value=" + value +
                '}';
    }
}
