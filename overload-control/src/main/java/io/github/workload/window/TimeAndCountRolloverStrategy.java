package io.github.workload.window;

/**
 * 基于时间和请求数量的窗口切换策略.
 */
public class TimeAndCountRolloverStrategy implements WindowRolloverStrategy<TimeAndCountWindowState> {

    @Override
    public boolean shouldRollover(TimeAndCountWindowState currentWindow, long nowNs, WindowConfig config) {
        return currentWindow.requested() >= config.getRequestCycle() ||
                (nowNs - currentWindow.getStartNs()) >= config.getTimeCycleNs();
    }
}
