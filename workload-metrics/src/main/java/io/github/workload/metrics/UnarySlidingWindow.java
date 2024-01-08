package io.github.workload.metrics;

import java.util.concurrent.atomic.LongAdder;

public class UnarySlidingWindow extends SlidingWindow<LongAdder> {

    public UnarySlidingWindow(int bucketCount, int intervalInMs) {
        super(bucketCount, intervalInMs);
    }

    @Override
    public LongAdder newEmptyBucket(long timeMillis) {
        return new LongAdder();
    }

    @Override
    protected WindowBucket<LongAdder> resetWindowTo(WindowBucket<LongAdder> bucket, long startTimeMillis) {
        bucket.resetStartTimeMillis(startTimeMillis);
        bucket.value().reset();
        return bucket;
    }
}
