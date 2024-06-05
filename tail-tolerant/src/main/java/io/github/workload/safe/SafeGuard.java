package io.github.workload.safe;

import lombok.Builder;
import lombok.Getter;

/**
 * 大报文保护参数.
 */
@Getter
@Builder
public class SafeGuard {
    private final int unsafeItemsThreshold;
    private final RateLimiter rateLimiter;

    @Getter
    private static class RateLimiter {
        private int costThreshold;

    }
}
