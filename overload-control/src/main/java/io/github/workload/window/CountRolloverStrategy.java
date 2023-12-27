package io.github.workload.window;

/**
 * 基于请求数量的窗口切换策略.
 */
public class CountRolloverStrategy implements WindowRolloverStrategy<CountWindowState> {

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean shouldRollover(CountWindowState currentWindow, long nowNs, WindowConfig config) {
        return currentWindow.requested() >= config.getRequestCycle();
    }
}
