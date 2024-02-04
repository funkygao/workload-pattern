package io.github.workload.overloading;

import io.github.workload.annotations.Immutable;
import lombok.AccessLevel;
import lombok.Getter;

@Immutable
class WorkloadFeedbackQueued implements WorkloadFeedback {
    @Getter(AccessLevel.PACKAGE)
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
