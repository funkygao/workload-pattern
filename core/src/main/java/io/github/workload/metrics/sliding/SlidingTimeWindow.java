package io.github.workload.metrics.sliding;

import io.github.workload.annotations.NotProductionReady;
import io.github.workload.annotations.NotThreadSafe;
import io.github.workload.annotations.ThreadSafe;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

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
@ThreadSafe
@NotProductionReady
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
     * @param bucketCount 该窗口由多少个桶构成，每个桶均分时间跨度
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
            return null;
        }

        int bucketIdx = calculateBucketIdx(timeMillis);
        long bucketStartMillis = calculateBucketStartMillis(timeMillis);
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

    private int calculateBucketIdx(long timeMillis) {
        long timeId = timeMillis / bucketDurationMs;
        return (int) (timeId % buckets.length());
    }

    private long calculateBucketStartMillis(long timeMillis) {
        return timeMillis - timeMillis % bucketDurationMs;
    }

}
