package io.github.workload.window;

/**
 * 基于请求数量的窗口切换策略.
 */
public abstract class CountRolloverStrategy implements WindowRolloverStrategy<CountWindowState> {

    @Override
    public boolean shouldRollover(CountWindowState currentWindow, long nowNs, WindowConfig<CountWindowState> config) {
        return currentWindow.requested() >= config.getRequestCycle();
    }

    @Override
    public CountWindowState createWindowState(long nowNs) {
        return new CountWindowState();
    }
}
