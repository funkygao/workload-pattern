package io.github.workload.overloading;

import io.github.workload.annotations.Immutable;
import lombok.AccessLevel;
import lombok.Getter;

@Immutable
class WorkloadFeedbackQueued implements WorkloadFeedback {
    @Getter(AccessLevel.PACKAGE)
    private final long queuedNs;

    WorkloadFeedbackQueued(long queuedNs) {
        this.queuedNs = queuedNs;
    }

}
