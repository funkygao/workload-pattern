package io.github.workload.greedy;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Consumer;

@Getter
public class GreedyConfig {
    private final int partitionSize;

    private final int greedyThreshold;
    private final Consumer<Integer> thresholdExceededAction;

    private final int limitCostsThreshold;
    private final GreedyLimiter greedyLimiter;
    private final String limiterKey;

    private GreedyConfig(Builder builder) {
        this.partitionSize = builder.partitionSize;
        this.greedyThreshold = builder.greedyThreshold;
        this.thresholdExceededAction = builder.thresholdExceededAction;
        this.limitCostsThreshold = builder.limitCostsThreshold;
        this.greedyLimiter = builder.greedyLimiter;
        this.limiterKey = builder.limiterKey;
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static GreedyConfig newDefault() {
        return new Builder()
                .build();
    }

    public static GreedyConfig newDefaultWithLimiter(int costsThreshold, @NonNull String limiterKey, @NonNull GreedyLimiter limiter) {
        return new Builder()
                .throttle(costsThreshold, limiterKey, limiter)
                .build();
    }

    @Slf4j
    public static class Builder {
        private int partitionSize = 100;

        private int greedyThreshold = 1000;
        private Consumer<Integer> thresholdExceededAction;

        private int limitCostsThreshold = Integer.MAX_VALUE;
        private String limiterKey;
        private GreedyLimiter greedyLimiter;

        public Builder partitionSize(int partitionSize) {
            this.partitionSize = partitionSize;
            return this;
        }

        public Builder greedyThreshold(int greedyThreshold) {
            this.greedyThreshold = greedyThreshold;
            return this;
        }

        public Builder throttle(int costsThreshold, @NonNull String limiterKey, @NonNull GreedyLimiter limiter) {
            this.limitCostsThreshold = costsThreshold;
            this.limiterKey = limiterKey;
            this.greedyLimiter = limiter;
            return this;
        }

        public Builder thresholdExceededAction(Consumer<Integer> action) {
            this.thresholdExceededAction = action;
            return this;
        }

        public GreedyConfig build() {
            if (partitionSize <= 0) {
                throw new IllegalArgumentException("partitionSize must be greater than 0");
            }
            if (greedyThreshold <= partitionSize) {
                throw new IllegalArgumentException("greedyThreshold must be greater than partitionSize");
            }
            if (greedyLimiter != null) {
                if (limiterKey == null || limiterKey.trim().isEmpty()) {
                    throw new IllegalArgumentException("greedyLimiter not null, limiterKey cannot be empty");
                }
                if (limitCostsThreshold == Integer.MAX_VALUE) {
                    throw new IllegalArgumentException("greedyLimiter not null, costsThreshold must be set");
                }
            }
            return new GreedyConfig(this);
        }
    }
}
