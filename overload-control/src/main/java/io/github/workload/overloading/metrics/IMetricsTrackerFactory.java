package io.github.workload.overloading.metrics;

public interface IMetricsTrackerFactory {

    IMetricsTracker create(String name);
}
