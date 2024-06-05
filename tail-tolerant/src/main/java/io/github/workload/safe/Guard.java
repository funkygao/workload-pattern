package io.github.workload.safe;

import lombok.Getter;

@Getter
public class Guard {
    private final int batchSize;
    private final int unsafeItemsThreshold;
    private final RateLimiter rateLimiter;

    private Guard(Builder builder) {
        this.batchSize = builder.batchSize;
        this.unsafeItemsThreshold = builder.unsafeItemsThreshold;
        this.rateLimiter = builder.rateLimiter;
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    private static Guard newDefault() {
        return newBuilder()
                .batchSize(100)
                .build();
    }

    private static class RateLimiter {
        private int costThreshold;

    }

    public static class Builder {
        private int batchSize;
        private int unsafeItemsThreshold;
        private RateLimiter rateLimiter;

        public Builder batchSize(int batchSize) {
            this.batchSize = batchSize;
            return this;
        }

        public Builder unsafeItemsThreshold(int threshold) {
            this.unsafeItemsThreshold = threshold;
            return this;
        }

        private Builder costRateLimit(int costGreaterThan) {
            this.rateLimiter = new RateLimiter();
            rateLimiter.costThreshold = costGreaterThan;
            return this;
        }

        public Guard build() {
            if (batchSize <= 0) {
                throw new IllegalArgumentException("batchSize must be greater than 0");
            }
            if (unsafeItemsThreshold < batchSize) {
                throw new IllegalArgumentException("unsafeItemsThreshold must be greater than batchSize");
            }

            return new Guard(this);
        }

    }
}
