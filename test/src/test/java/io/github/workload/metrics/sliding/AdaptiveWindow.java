package io.github.workload.metrics.sliding;

import io.github.workload.annotations.VisibleForTesting;

import java.util.concurrent.atomic.LongAdder;

class AdaptiveWindow extends SlidingTimeWindow<AdaptiveWindow.Counter> {

    AdaptiveWindow(int bucketCount, int windowDurationMs) {
        super(bucketCount, windowDurationMs);
    }

    @Override
    protected Counter newEmptyBucketData(long timeMillis) {
        return new Counter();
    }

    @Override
    protected Bucket<Counter> resetBucket(Bucket<Counter> bucket, long startTimeMillis) {
        bucket.data().reset();
        return bucket;
    }

    public static class Counter {
        LongAdder totalMessages = new LongAdder();
        LongAdder urgentMessages = new LongAdder();

        void reset() {
            totalMessages.reset();
            urgentMessages.reset();
        }

        void sendMessage(boolean urgent) {
            totalMessages.increment();
            if (urgent) {
                urgentMessages.increment();
            }
        }

        @VisibleForTesting
        double urgentPercent() {
            final long total = totalMessages.longValue();
            if (total == 0) {
                return 0;
            }

            return urgentMessages.doubleValue() / total;
        }
    }
}

