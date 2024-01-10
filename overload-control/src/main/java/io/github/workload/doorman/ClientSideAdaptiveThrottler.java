package io.github.workload.doorman;

import io.github.workload.annotations.VisibleForTesting;
import io.github.workload.metrics.Bucket;
import io.github.workload.metrics.SlidingTimeWindow;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

/**
 * Implementation of <a href="https://sre.google/sre-book/handling-overload/#eq2101">Client request rejection probability</a>.
 */
@Slf4j
public class ClientSideAdaptiveThrottler {
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
    final SlidingTimeWindow<ClientRequestMetric> window;

    public ClientSideAdaptiveThrottler(double multiplier) {
        if (multiplier <= 1) {
            throw new IllegalArgumentException("multiplier must be above 1.0");
        }

        this.multiplier = multiplier;
        this.window = createWindow(TWO_MINUTES_MS);
    }

    public boolean attemptRequest() {
        Bucket<ClientRequestMetric> bucket = window.currentBucket();
        ClientRequestMetric data = bucket.data();
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

    private SlidingTimeWindow<ClientRequestMetric> createWindow(int windowDurationMs) {
        return new SlidingTimeWindow<ClientRequestMetric>(1, windowDurationMs) {
            @Override
            protected ClientRequestMetric newEmptyBucketData(long timeMillis) {
                return new ClientRequestMetric();
            }

            @Override
            protected Bucket<ClientRequestMetric> resetBucket(Bucket<ClientRequestMetric> bucket, long startTimeMillis) {
                bucket.data().reset();
                return bucket;
            }
        };
    }
}
