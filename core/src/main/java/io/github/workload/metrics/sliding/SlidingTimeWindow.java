package io.github.workload.metrics.sliding;

import io.github.workload.annotations.NotThreadSafe;
import io.github.workload.annotations.ThreadSafe;
import io.github.workload.annotations.VisibleForTesting;
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
 * <pre>
 * +-------------------------------------------------------------------+
 * | SlidingTimeWindow                                                 |
 * |+----------------------------------+                               |
 * || AtomicReferenceArray<Bucket>     |                               |
 * || +---+---+---+---+---+---+---+---+|                               |
 * || | 0 | 1 | 2 | 3 |...| i |...| n ||                               |
 * || +---+---+---+---+---+---+---+---+|                               |
 * ||     ^                           ^|                               |
 * ||     |                           ||                               |
 * ||     +---- The array wraps ----->||                               |
 * |+----------------------------------+                               |
 * |                                                                   |
 * | [Bucket Duration] = Total Window Duration / (n+1)                 |
 * |                                                                   |
 * |  Bucket 0         Bucket 1        ...        Bucket i  ...        |
 * | +------------+   +------------+             +------------+        |
 * | |            |   |            |             |            |        |
 * | | Statistic  |   | Statistic  |             | Statistic  |        |
 * | |   Data     |   |   Data     |             |   Data     |        |
 * | |            |   |            |             |            |        |
 * | +------------+   +------------+             +------------+        |
 * |     |                                          |                  |
 * |     |<-- Oldest                                | Newest -->|      |
 * |     |                                          |                  |
 * | <-------------- Total Window Duration --------------------------->|
 * |                                                                   |
 * +-------------------------------------------------------------------+
 * </pre>
 *
 * @param <StatisticData> type of statistic data
 */
@Slf4j
@ToString
@ThreadSafe
public abstract class SlidingTimeWindow<StatisticData> {
    protected final int bucketCount; // how many buckets in the sliding window
    protected final int bucketDurationMs; // time span of each bucket

    /**
     * CAS circular array.
     */
    @ToString.Exclude
    protected final AtomicReferenceArray<Bucket<StatisticData>> buckets;

    @ToString.Exclude
    private final ReentrantLock updateLock = new ReentrantLock();

    protected abstract StatisticData newEmptyBucketData(long timeMillis);

    @NotThreadSafe(serial = true)
    protected abstract Bucket<StatisticData> resetBucket(Bucket<StatisticData> bucket, long startTimeMillis);

    /**
     * Constructor.
     *
     * @param bucketCount      该窗口由多少个桶构成，每个桶均分时间跨度
     * @param windowDurationMs 该窗口要保留最近多长时间的统计数据
     */
    public SlidingTimeWindow(int bucketCount, int windowDurationMs) {
        this.bucketCount = bucketCount;
        this.bucketDurationMs = windowDurationMs / bucketCount;
        this.buckets = new AtomicReferenceArray<>(bucketCount);
    }

    public Bucket<StatisticData> currentBucket() {
        return currentBucket(System.currentTimeMillis());
    }

    public Bucket<StatisticData> currentBucket(long timeMillis) {
        if (timeMillis < 0) {
            log.error("invalid timeMillis:{}, returns null", timeMillis);
            return null;
        }

        final int bucketIdx = calculateBucketIdx(timeMillis);
        final long bucketStartMillis = calculateBucketStartMillis(timeMillis);
        log.trace("{}, bucket:{}, windowStart:{}", timeMillis, bucketIdx, bucketStartMillis);
        while (true) {
            Bucket<StatisticData> present = buckets.get(bucketIdx);
            if (present == null) {
                Bucket<StatisticData> bucket = new Bucket<>(bucketDurationMs, bucketStartMillis, newEmptyBucketData(timeMillis));
                // 采用乐观锁CAS保证环形数组更新的原子性
                if (buckets.compareAndSet(bucketIdx, null, bucket)) {
                    log.trace("create {}", bucket);
                    return bucket;
                } else {
                    // 下一个循环就拿到已创建的bucket了
                    Thread.yield();
                }
            } else if (bucketStartMillis == present.startMillis()) {
                log.trace("reuse {}", present);
                return present;
            } else if (bucketStartMillis > present.startMillis()) {
                // 旧桶开始时间落后于提供的时间，意味着旧桶已弃用，又过了N个窗口周期：重置后复用
                if (updateLock.tryLock()) { // 利用锁保护reset的原子性
                    try {
                        log.trace("reuse stale {}", present);
                        present.resetStartTimeMillis(bucketStartMillis);
                        return resetBucket(present, bucketStartMillis);
                    } finally {
                        updateLock.unlock();
                    }
                } else {
                    Thread.yield();
                }
            } else if (bucketStartMillis < present.startMillis()) {
                // 提供的时间落后于当前bucket开始时间，通常是NTP时钟回拨导致
                log.warn("should never happen, clock drift backwards? {} < {}", bucketStartMillis, present.startMillis());
                return new Bucket<>(bucketDurationMs, bucketStartMillis, newEmptyBucketData(timeMillis));
            }
        }
    }

    /**
     * Get aggregated value list for entire sliding window.
     *
     * @return aggregated value list for entire sliding window
     */
    public List<StatisticData> values() {
        return values(System.currentTimeMillis());
    }

    @VisibleForTesting
    List<StatisticData> values(long timeMillis) {
        List<StatisticData> result = new ArrayList<>(bucketCount);
        for (int i = 0; i < bucketCount; i++) {
            Bucket<StatisticData> bucket = buckets.get(i);
            if (bucket == null || !bucket.isTimeInBucket(timeMillis)) {
                continue;
            }

            result.add(bucket.data());
        }
        return result;
    }

    @VisibleForTesting
    int calculateBucketIdx(long timeMillis) {
        long timeId = timeMillis / bucketDurationMs;
        return (int) (timeId % bucketCount);
    }

    private long calculateBucketStartMillis(long timeMillis) {
        return timeMillis - timeMillis % bucketDurationMs;
    }

}
