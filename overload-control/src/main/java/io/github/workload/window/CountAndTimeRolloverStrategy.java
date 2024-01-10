package io.github.workload.window;

/**
 * 基于时间和请求数量的窗口切换策略.
 */
public abstract class CountAndTimeRolloverStrategy implements WindowRolloverStrategy<CountAndTimeWindowState> {

    @Override
    public final boolean shouldRollover(CountAndTimeWindowState currentWindow, long nowNs, WindowConfig<CountAndTimeWindowState> config) {
        return currentWindow.requested() >= config.getRequestCycle() ||
                (nowNs - currentWindow.getStartNs()) >= config.getTimeCycleNs();
    }

    @Override
    public final CountAndTimeWindowState createWindowState(long nowNs) {
        return new CountAndTimeWindowState(nowNs);
    }
}
