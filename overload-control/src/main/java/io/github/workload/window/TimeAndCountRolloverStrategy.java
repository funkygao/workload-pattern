package io.github.workload.window;

public class TimeAndCountRolloverStrategy implements WindowRolloverStrategy<TimeAndCountWindowState> {

    @Override
    public boolean shouldRollover(TimeAndCountWindowState currentWindow, long nowNs, WindowConfig config) {
        return currentWindow.requested() >= config.getRequestCycle() ||
                (nowNs - currentWindow.getStartNs()) >= config.getTimeCycleNs();
    }
}
