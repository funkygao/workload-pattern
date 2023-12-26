package io.github.workload.window;

public class CountOnlyRolloverStrategy implements WindowRolloverStrategy {

    @Override
    public boolean shouldRollover(WindowState currentWindow, long nowNs, WindowConfig config) {
        return currentWindow.requested() > config.getRequestCycle();
    }
}
