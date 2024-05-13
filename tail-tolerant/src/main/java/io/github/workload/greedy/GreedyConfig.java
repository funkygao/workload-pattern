package io.github.workload.greedy;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Consumer;

@Getter
public class GreedyConfig {
    private final int partitionSize;

    private final int greedyThreshold;
    private final Consumer<Integer> thresholdExceededAction;

    private final int costsThreshold;
    private final GreedyLimiter greedyLimiter;
    private final String limiterKey;

    private GreedyConfig(Builder builder) {
        this.partitionSize = builder.partitionSize;
        this.greedyThreshold = builder.greedyThreshold;
        this.thresholdExceededAction = builder.thresholdExceededAction;
        this.costsThreshold = builder.costsThreshold;
        this.greedyLimiter = builder.greedyLimiter;
        this.limiterKey = builder.limiterKey;
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    @Slf4j
    public static class Builder {
        private int partitionSize = 100;
        private int greedyThreshold = Integer.MAX_VALUE;
        private int costsThreshold = Integer.MAX_VALUE;
        private String limiterKey;
        private GreedyLimiter greedyLimiter;
        private Consumer<Integer> thresholdExceededAction = itemsProcessed -> log.warn("Items processed exceed threshold: {} > {}", itemsProcessed, greedyThreshold);

        public Builder partitionSize(int partitionSize) {
            this.partitionSize = partitionSize;
            return this;
        }

        public Builder greedyThreshold(int greedyThreshold) {
            this.greedyThreshold = greedyThreshold;
            return this;
        }

        public Builder costsThreshold(int costsThreshold) {
            this.costsThreshold = costsThreshold;
            return this;
        }

        public Builder limiterKey(String limiterKey) {
            this.limiterKey = limiterKey;
            return this;
        }

        private Builder greedyLimiter(GreedyLimiter limiter) {
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
            return new GreedyConfig(this);
        }
    }
}
