package io.github.workload.metrics.tumbling.flexibility;

import io.github.workload.metrics.tumbling.WindowConfig;
import io.github.workload.metrics.tumbling.WindowRolloverStrategy;

public abstract class FooRolloverStrategy implements WindowRolloverStrategy<FooWindowState> {
    @Override
    public boolean shouldRollover(FooWindowState currentWindow, long nowNs, WindowConfig<FooWindowState> config) {
        return currentWindow.requested() > 100;
    }

    @Override
    public FooWindowState createWindowState(long nowNs) {
        return new FooWindowState();
    }
}
