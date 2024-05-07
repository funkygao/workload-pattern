package io.github.workload.overloading.metrics;

import io.github.workload.WorkloadPriority;

public interface IMetricsTracker extends AutoCloseable {

    default void enter(WorkloadPriority priority) {}

    default void shedByCpu(WorkloadPriority priority) {}

    default void shedByQueue(WorkloadPriority priority) {}

    @Override
    default void close() {}
}
