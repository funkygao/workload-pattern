package io.github.workload;

import io.github.workload.annotations.Immutable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;

/**
 * 工作负荷.
 *
 * <p>Any execution computational unit，常见有：HTTP/RPC Request/Task/Consumed Message.</p>
 */
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public class Workload {
    @Immutable
    private final WorkloadPriority priority;

    private double cost;
    private int retryAttempted;

    public static Workload ofPriority(@NonNull WorkloadPriority priority) {
        return new Workload(priority, 0, 0);
    }

    public Workload withCost(double cost) {
        if (cost < 0) {
            throw new IllegalArgumentException("cost cannot be negative");
        }

        this.cost = cost;
        return this;
    }

    public Workload withRetryAttempted(int retryAttempted) {
        if (retryAttempted < 0) {
            throw new IllegalArgumentException("retryAttempted cannot be negative");
        }

        this.retryAttempted = retryAttempted;
        return this;
    }
}
