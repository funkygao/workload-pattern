package io.github.workload.window.flexibility;

import io.github.workload.WorkloadPriority;
import io.github.workload.window.WindowConfig;
import io.github.workload.window.WindowState;

public class FooWindowState extends WindowState {
    @Override
    protected void doSample(WorkloadPriority priority, boolean admitted) {

    }

    @Override
    protected void logRollover(String prefix, long nowNs, WindowState nextWindow, WindowConfig config) {
        System.out.printf("%d -> %d\n", this.hashCode(), nextWindow.hashCode());
    }
}
