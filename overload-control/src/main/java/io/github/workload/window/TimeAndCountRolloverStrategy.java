package io.github.workload.window;

public class TimeAndCountRolloverStrategy implements WindowRolloverStrategy {

    @Override
    public boolean shouldRollover(WindowState currentWindow, long nowNs, WindowConfig config) {
        return currentWindow.requested() > config.getRequestCycle() ||
                (nowNs - currentWindow.getStartNs()) > config.getTimeCycleNs();
    }
}
