package io.github.workload.doorman;

import io.github.workload.annotations.PoC;
import io.github.workload.annotations.VisibleForTesting;
import io.github.workload.metrics.sliding.Bucket;
import io.github.workload.metrics.sliding.SlidingTimeWindow;
import lombok.Generated;

import java.util.concurrent.TimeUnit;

/**
 * Implementation of <a href="https://sre.google/sre-book/handling-overload/#eq2101">Client request rejection probability</a>.
 */
@PoC
@Generated
public class ThrottlingClient {
    private static final int TWO_MINUTES_MS = (int) TimeUnit.MILLISECONDS.convert(2, TimeUnit.MINUTES);

    /**
     * <ul>K
     * <li>Reducing the multiplier will make adaptive throttling behave more aggressively</li>
     * <li>Increasing the multiplier will make adaptive throttling behave less aggressively</li>
     * </ul>
     */
    private final double multiplier;

    // for last 2 minutes
    @VisibleForTesting
    final SlidingTimeWindow<Metric> window;

    public ThrottlingClient(double multiplier) {
        if (multiplier <= 1) {
            throw new IllegalArgumentException("multiplier must be above 1.0");
        }

        this.multiplier = multiplier;
        this.window = createWindow(TWO_MINUTES_MS);
    }

    public boolean attemptRequest() {
        Bucket<Metric> bucket = window.currentBucket();
        Metric data = bucket.data();
        final int accepts = data.accepts();
        final int requests = data.requests();
        final boolean shouldAllowRequest = shouldAllowRequest(requests, accepts);
        data.localPass(shouldAllowRequest);
        return shouldAllowRequest;
    }

    private boolean shouldAllowRequest(int requests, int accepts) {
        if (accepts == 0) {
            return true;
        }

        double ratio = (double) requests / accepts;
        return ratio < multiplier;
    }

    public void backendRejected() {
        // If the request was rejected by backend, we decrease the total accept count to eventually
        // reduce the request-to-accept ratio, this makes the throttling more aggressive
        window.currentBucket().data().backendRejected();
    }

    private SlidingTimeWindow<Metric> createWindow(int windowDurationMs) {
        return new SlidingTimeWindow<Metric>(1, windowDurationMs) {
            @Override
            protected Metric newEmptyBucketData(long timeMillis) {
                return new Metric();
            }

            @Override
            protected Bucket<Metric> resetBucket(Bucket<Metric> bucket, long startTimeMillis) {
                bucket.data().reset();
                return bucket;
            }
        };
    }
}
