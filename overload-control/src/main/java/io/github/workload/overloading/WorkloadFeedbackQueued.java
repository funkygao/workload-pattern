package io.github.workload.overloading;

import io.github.workload.annotations.Immutable;
import lombok.Getter;

@Getter
@Immutable
public class WorkloadFeedbackQueued implements WorkloadFeedback {
    private final long queuedNs;

    /**
     * Queueing time of a workload.
     *
     * <p>{@code Time(request processing being started at the server) - Time(request arrival)}</p>
     */
    WorkloadFeedbackQueued(long queuedNs) {
        this.queuedNs = queuedNs;
    }

}
