package io.github.workload.metrics.sliding;

import lombok.ToString;

import java.util.concurrent.atomic.LongAdder;

class SimpleErrorSlidingTimeWindow extends SlidingTimeWindow<SimpleErrorSlidingTimeWindow.SimpleErrorCounter> {

    SimpleErrorSlidingTimeWindow(int bucketCount, int windowDurationMs) {
        super(bucketCount, windowDurationMs);
    }

    @Override
    public SimpleErrorCounter newEmptyBucketData(long timeMillis) {
        return new SimpleErrorCounter();
    }

    @Override
    protected Bucket<SimpleErrorCounter> resetBucket(Bucket<SimpleErrorCounter> bucket, long startTimeMillis) {
        bucket.data().reset();
        return bucket;
    }

    @ToString
    public static class SimpleErrorCounter {
        public LongAdder err = new LongAdder();
        public LongAdder total = new LongAdder();

        public void reset() {
            err.reset();
            total.reset();
        }
    }

}
