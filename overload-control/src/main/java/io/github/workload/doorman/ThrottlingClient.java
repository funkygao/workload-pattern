package io.github.workload.doorman;

import io.github.workload.annotations.VisibleForTesting;
import io.github.workload.metrics.sliding.Bucket;
import io.github.workload.metrics.sliding.SlidingTimeWindow;

import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * 自适应的客户端主动限流器，旨在防止后端服务过载(如果后端拒绝成本不能忽略时).
 *
 * <p>通过在客户端预先拒绝一部分请求来实现，而不是让所有请求都发送到后端服务再被拒绝.</p>
 *
 * Implementation of <a href="https://sre.google/sre-book/handling-overload/#eq2101">Client request rejection probability</a>.
 *
 * <ul>应对调用第三方接口的坑：
 * <li>欠费了</li>
 * <li>被限流了</li>
 * <li>token失效了</li>
 * </ul>
 */
public class ThrottlingClient {
    private static final int TWO_MINUTES_MS = (int) TimeUnit.MILLISECONDS.convert(2, TimeUnit.MINUTES);
    private static final Random random = new Random();

    /**
     * 用来控制限流的敏感性：(1.0, ∞]，Google通常使用2.0.
     *
     * <p>系统预留了多少额外容量，在超出这个容量之前不会开始限流.</p>
     * <ul>
     *     <li>K值大，则限流不那么积极，允许更多的请求到达后端，会在后端浪费更多资源，但这也加快了从后端到客户端状态的传播</li>
     *     <li>K值小，则限流积极，例如：1.1，1.2， 1.3</li>
     * </ul>
     */
    private final double K;

    // for last 2 minutes
    @VisibleForTesting
    final SlidingTimeWindow<Metric> window;

    public ThrottlingClient(double K) {
        if (K <= 1) {
            throw new IllegalArgumentException("K must > 1.0");
        }

        this.K = K;
        this.window = new SlidingTimeWindow<Metric>(1, TWO_MINUTES_MS) {
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

    public boolean requestAllows() {
        Metric metric = window.currentBucket().data();
        final int accepts = metric.accepts();
        final int requests = metric.requests();
        final boolean reject = random.nextDouble() < rejectionProbability(K, requests, accepts);
        boolean allow = !reject;
        metric.localPass(allow);
        return allow;
    }

    public void backendRejected() {
        // If the request was rejected by backend, we decrease the total accept count to eventually
        // reduce the request-to-accept ratio, this makes the throttling more aggressive
        window.currentBucket().data().backendRejected();
    }

    @VisibleForTesting
    double rejectionProbability(double K, int requests, int accepts) {
        // 如果请求的数量没有显著超过接受的数量（乘以K），那么p将是零或一个负数，这意味着不会拒绝请求
        // 如果超过了，那么p就会是一个正数，表示有一定概率拒绝新的请求
        double p = ((double) requests - K * accepts) / (requests + 1);
        return Math.max(0, p);
    }

}
