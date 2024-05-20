package io.github.workload.greedy;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Consumer;

@Getter
public class GreedyConfig {
    private final int batchSize;

    private final int itemsLimit;
    private final Consumer<Integer> onItemsLimitExceed;

    private final int rateLimitOnCostExceed;
    private final GreedyLimiter rateLimiter;
    private final String rateLimiterKey;

    private GreedyConfig(Builder builder) {
        this.batchSize = builder.batchSize;

        this.itemsLimit = builder.itemsLimit;
        this.onItemsLimitExceed = builder.onItemsLimitExceed;

        this.rateLimitOnCostExceed = builder.rateLimitOnCostExceed;
        this.rateLimiter = builder.rateLimiter;
        this.rateLimiterKey = builder.rateLimiterKey;
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static GreedyConfig newDefault() {
        return new Builder()
                .build();
    }

    public static GreedyConfig newDefaultWithLimiter(int rateLimitOnCostExceed, @NonNull String rateLimiterKey, @NonNull GreedyLimiter rateLimiter) {
        return new Builder()
                .throttle(rateLimitOnCostExceed, rateLimiterKey, rateLimiter)
                .build();
    }

    @Slf4j
    public static class Builder {
        private int batchSize = 100;

        private int itemsLimit = 1000;
        private Consumer<Integer> onItemsLimitExceed;

        private int rateLimitOnCostExceed = Integer.MAX_VALUE;
        private String rateLimiterKey;
        private GreedyLimiter rateLimiter;

        public Builder batchSize(int batchSize) {
            this.batchSize = batchSize;
            return this;
        }

        public Builder itemsLimit(int itemsLimit) {
            this.itemsLimit = itemsLimit;
            return this;
        }

        public Builder throttle(int rateLimitOnCostExceed, @NonNull String rateLimiterKey, @NonNull GreedyLimiter rateLimiter) {
            this.rateLimitOnCostExceed = rateLimitOnCostExceed;
            this.rateLimiterKey = rateLimiterKey;
            this.rateLimiter = rateLimiter;
            return this;
        }

        public Builder onItemsLimitExceed(Consumer<Integer> onItemsLimitExceed) {
            this.onItemsLimitExceed = onItemsLimitExceed;
            return this;
        }

        public GreedyConfig build() {
            if (batchSize <= 0) {
                throw new IllegalArgumentException("partitionSize must be greater than 0");
            }
            if (itemsLimit <= batchSize) {
                throw new IllegalArgumentException("greedyThreshold must be greater than partitionSize");
            }
            if (rateLimiter != null) {
                if (rateLimiterKey == null || rateLimiterKey.trim().isEmpty()) {
                    throw new IllegalArgumentException("greedyLimiter not null, limiterKey cannot be empty");
                }
                if (rateLimitOnCostExceed == Integer.MAX_VALUE) {
                    throw new IllegalArgumentException("greedyLimiter not null, costsThreshold must be set");
                }
            }
            return new GreedyConfig(this);
        }
    }
}
