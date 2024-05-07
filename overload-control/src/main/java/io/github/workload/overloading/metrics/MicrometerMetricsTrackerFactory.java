package io.github.workload.overloading.metrics;

import io.micrometer.core.instrument.MeterRegistry;

public class MicrometerMetricsTrackerFactory implements IMetricsTrackerFactory {
    private final MeterRegistry registry;

    public MicrometerMetricsTrackerFactory(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public IMetricsTracker create(String name) {
        return new MicrometerMetricsTracker(name, registry);
    }
}
