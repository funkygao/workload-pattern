package io.github.workload.overloading.metrics;

import io.github.workload.WorkloadPriority;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * {@link IMetricsTracker Metrics tracker} for Micrometer.
 */
public class MicrometerMetricsTracker implements IMetricsTracker {
    private static final String METRIC_NAME = "workload.admission";

    private final MeterRegistry meterRegistry;

    private final Counter total;
    private final Counter shedByCpu;
    private final Counter shedByQueue;

    public MicrometerMetricsTracker(String name, MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        total = Counter.builder(METRIC_NAME)
                .tag(name, "total")
                .register(meterRegistry);
        shedByCpu = Counter.builder(METRIC_NAME)
                .tag(name, "shed_cpu")
                .register(meterRegistry);
        shedByQueue = Counter.builder(METRIC_NAME)
                .tag(name, "shed_queue")
                .register(meterRegistry);
    }

    @Override
    public void enter(WorkloadPriority priority) {
        total.increment();
    }

    @Override
    public void shedByCpu(WorkloadPriority priority) {
        shedByCpu.increment();
    }

    @Override
    public void shedByQueue(WorkloadPriority priority) {
        shedByQueue.increment();
    }

    @Override
    public void close() {
        meterRegistry.remove(total);
        meterRegistry.remove(shedByCpu);
        meterRegistry.remove(shedByQueue);
    }
}
