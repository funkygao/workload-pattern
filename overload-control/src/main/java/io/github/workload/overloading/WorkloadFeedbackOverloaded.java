package io.github.workload.overloading;

import io.github.workload.annotations.Immutable;
import lombok.AccessLevel;
import lombok.Getter;

@Immutable
class WorkloadFeedbackOverloaded implements WorkloadFeedback {
    @Getter(AccessLevel.PACKAGE)
    private final long overloadedAtNs;

    WorkloadFeedbackOverloaded(long overloadedAtNs) {
        this.overloadedAtNs = overloadedAtNs;
    }

}
