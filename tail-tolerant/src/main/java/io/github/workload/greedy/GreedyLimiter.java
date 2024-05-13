package io.github.workload.greedy;

import io.github.workload.CostAware;

/**
 * Fine-grained {@link CostAware} based rate limiter.
 */
public interface GreedyLimiter {

    boolean canAcquire(String key, int permits);

}
