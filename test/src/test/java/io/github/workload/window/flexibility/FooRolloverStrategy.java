package io.github.workload.window.flexibility;

import io.github.workload.window.WindowConfig;
import io.github.workload.window.WindowRolloverStrategy;

public class FooRolloverStrategy implements WindowRolloverStrategy<FooWindowState> {
    @Override
    public boolean shouldRollover(FooWindowState currentWindow, long nowNs, WindowConfig<FooWindowState> config) {
        return currentWindow.requested() > 100;
    }

    @Override
    public FooWindowState createWindowState(long nowNs) {
        return new FooWindowState();
    }
}
