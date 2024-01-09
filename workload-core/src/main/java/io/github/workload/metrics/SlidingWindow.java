package io.github.workload.metrics;

import lombok.NonNull;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Sliding window algorithm的实现.
 *
 * <p>Borrowed from alibaba Sentinel LeapArray, with some tweaks for understanding.</p>
 *
 * @param <StatisticData> type of statistic data
 */
@Slf4j
@ToString
public abstract class SlidingWindow<StatisticData> {
    protected final int bucketCount; // how many buckets in the sliding window
    protected final int bucketLengthInMs; // time span of each bucket
    @ToString.Exclude
    protected final AtomicReferenceArray<WindowBucket<StatisticData>> buckets; // CAS circular array

    @ToString.Exclude
    private final ReentrantLock updateLock = new ReentrantLock();

    protected abstract StatisticData newEmptyBucket(long timeMillis);

    protected abstract WindowBucket<StatisticData> resetBucket(WindowBucket<StatisticData> bucket, long startTimeMillis);

    public SlidingWindow(int bucketCount, int intervalInMs) {
        this.bucketCount = bucketCount;
        this.bucketLengthInMs = intervalInMs / bucketCount;
        this.buckets = new AtomicReferenceArray<>(bucketCount);
    }

    private int calculateBucketIdx(long timeMillis) {
        long timeId = timeMillis / bucketLengthInMs;
        return (int) (timeId % buckets.length());
    }

    protected long calculateBucketStartMillis(long timeMillis) {
        return timeMillis - timeMillis % bucketLengthInMs;
    }

    public StatisticData getWindowData(long timeMillis) {
        if (timeMillis < 0) {
            return null;
        }

        int bucketIdx = calculateBucketIdx(timeMillis);
        WindowBucket<StatisticData> bucket = buckets.get(bucketIdx);
        if (bucket == null || !bucket.isTimeInBucket(timeMillis)) {
            return null;
        }

        return bucket.data();
    }

    public boolean isBucketDeprecated(long timeMillis, @NonNull WindowBucket<StatisticData> bucket) {
        return timeMillis - bucket.startMillis() > (bucketLengthInMs * bucketCount);
    }

    public List<WindowBucket<StatisticData>> list(long timeMillis) {
        List<WindowBucket<StatisticData>> result = new ArrayList<>(bucketCount);

        for (int i = 0; i < bucketCount; i++) {
            WindowBucket<StatisticData> bucket = buckets.get(i);
            if (bucket == null || isBucketDeprecated(timeMillis, bucket)) {
                continue;
            }
            result.add(bucket);
        }

        return result;
    }

    public WindowBucket<StatisticData> currentBucket(long timeMillis) {
        if (timeMillis < 0) {
            return null;
        }

        int bucketIdx = calculateBucketIdx(timeMillis);
        long bucketStartMillis = calculateBucketStartMillis(timeMillis);
        log.trace("{}, bucket:{}, windowStart:{}", timeMillis, bucketIdx, bucketStartMillis);
        while (true) {
            WindowBucket<StatisticData> present = buckets.get(bucketIdx);
            if (present == null) {
                WindowBucket<StatisticData> bucket = new WindowBucket<>(bucketLengthInMs, bucketStartMillis, newEmptyBucket(timeMillis));
                // 采用乐观锁CAS保证线程安全
                if (buckets.compareAndSet(bucketIdx, null, bucket)) {
                    log.trace("create {}", bucket);
                    return bucket;
                } else {
                    Thread.yield();
                }
            } else if (bucketStartMillis == present.startMillis()) {
                log.trace("reuse {}", present);
                return present;
            } else if (bucketStartMillis > present.startMillis()) {
                if (updateLock.tryLock()) {
                    try {
                        log.trace("reuse stale {}", present);
                        return resetBucket(present, bucketStartMillis);
                    } finally {
                        updateLock.unlock();
                    }
                } else {
                    Thread.yield();
                }
            } else if (bucketStartMillis < present.startMillis()) {
                log.warn("should never happen, {}", this);
                return new WindowBucket<>(bucketLengthInMs, bucketStartMillis, newEmptyBucket(timeMillis));
            }
        }
    }

}
