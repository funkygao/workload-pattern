package io.github.workload.metrics;

import lombok.NonNull;
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
 * @param <T> type of statistic data
 */
@Slf4j
public abstract class SlidingWindow<T> {
    protected final int bucketCount; // how many buckets in the sliding window
    protected final int bucketLengthInMs; // time span of each bucket
    protected final AtomicReferenceArray<WindowBucket<T>> buckets; // circular array

    private final ReentrantLock updateLock = new ReentrantLock();

    public abstract T newEmptyBucket(long timeMillis);

    protected abstract WindowBucket<T> resetWindowTo(WindowBucket<T> bucket, long startTimeMillis);

    public SlidingWindow(int bucketCount, int intervalInMs) {
        this.bucketCount = bucketCount;
        this.bucketLengthInMs = intervalInMs / bucketCount;
        this.buckets = new AtomicReferenceArray<>(bucketCount);
    }

    private int calculateBucketIdx(long timeMillis) {
        long timeId = timeMillis / bucketLengthInMs;
        return (int) (timeId % buckets.length());
    }

    protected long calculateWindowStartMillis(long timeMillis) {
        return timeMillis - timeMillis % bucketLengthInMs;
    }

    public T getWindowValue(long timeMillis) {
        if (timeMillis < 0) {
            return null;
        }

        int bucketIdx = calculateBucketIdx(timeMillis);
        WindowBucket<T> bucket = buckets.get(bucketIdx);
        if (bucket == null || !bucket.isTimeInWindow(timeMillis)) {
            return null;
        }

        return bucket.value();
    }

    public boolean isWindowDeprecated(long time, @NonNull WindowBucket<T> bucket) {
        return time - bucket.windowStartMillis() > (bucketLengthInMs * bucketCount);
    }

    public List<WindowBucket<T>> list(long validTime) {
        List<WindowBucket<T>> result = new ArrayList<>(bucketCount);

        for (int i = 0; i < bucketCount; i++) {
            WindowBucket<T> bucket = buckets.get(i);
            if (bucket == null || isWindowDeprecated(validTime, bucket)) {
                continue;
            }
            result.add(bucket);
        }

        return result;
    }


    public WindowBucket<T> currentWindow(long timeMillis) {
        if (timeMillis < 0) {
            return null;
        }

        /*
         * 使用了环形数组后，带来2个问题：
         * 1) 怎么根据时间定位到对应的下标？
         * 2) 数组里的数据是新的还是旧的？
         */
        int bucketIdx = calculateBucketIdx(timeMillis);
        long windowStartMillis = calculateWindowStartMillis(timeMillis);
        log.trace("{}, bucket:{}, windowStart:{}", timeMillis, bucketIdx, windowStartMillis);
        while (true) {
            WindowBucket<T> present = buckets.get(bucketIdx);
            if (present == null) {
                WindowBucket<T> bucket = new WindowBucket<>(bucketLengthInMs, windowStartMillis, newEmptyBucket(timeMillis));
                // 采样乐观锁CAS保证线程安全
                if (buckets.compareAndSet(bucketIdx, null, bucket)) {
                    // CAS ok
                    log.trace("create {}", bucket);
                    return bucket;
                } else {
                    Thread.yield();
                }
            } else if (windowStartMillis == present.windowStartMillis()) {
                log.trace("reuse {}", present);
                return present;
            } else if (windowStartMillis > present.windowStartMillis()) {
                if (updateLock.tryLock()) {
                    try {
                        log.trace("reuse stale {}", present);
                        return resetWindowTo(present, windowStartMillis);
                    } finally {
                        updateLock.unlock();
                    }
                } else {
                    Thread.yield();
                }
            } else if (windowStartMillis < present.windowStartMillis()) {
                // should never happen
                log.trace("should never happen");
                return new WindowBucket<>(bucketLengthInMs, windowStartMillis, newEmptyBucket(timeMillis));
            }
        }
    }

}
