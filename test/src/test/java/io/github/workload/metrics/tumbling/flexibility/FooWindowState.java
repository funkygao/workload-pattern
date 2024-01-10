package io.github.workload.metrics.tumbling.flexibility;

import io.github.workload.WorkloadPriority;
import io.github.workload.metrics.tumbling.WindowConfig;
import io.github.workload.metrics.tumbling.WindowState;

public class FooWindowState extends WindowState {
    @Override
    protected void doSample(WorkloadPriority priority, boolean admitted) {

    }

    @Override
    protected void logRollover(String prefix, long nowNs, WindowState nextWindow, WindowConfig config) {
        System.out.printf("%d -> %d\n", this.hashCode(), nextWindow.hashCode());
    }
}
