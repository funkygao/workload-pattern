package io.github.workload.metrics;

import lombok.ToString;

import java.util.concurrent.atomic.LongAdder;

class SimpleErrorSlidingWindow extends SlidingWindow<SimpleErrorSlidingWindow.SimpleErrorCounter> {

    SimpleErrorSlidingWindow(int bucketCount, int intervalInMs) {
        super(bucketCount, intervalInMs);
    }

    @Override
    public SimpleErrorCounter newEmptyBucket(long timeMillis) {
        return new SimpleErrorCounter();
    }

    @Override
    protected WindowBucket<SimpleErrorCounter> resetWindowTo(WindowBucket<SimpleErrorCounter> bucket, long startTimeMillis) {
        bucket.resetStartTimeMillis(startTimeMillis);
        bucket.value().reset();
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
