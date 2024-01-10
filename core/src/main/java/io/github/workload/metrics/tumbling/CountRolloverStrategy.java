package io.github.workload.metrics.tumbling;

/**
 * 基于请求数量的窗口切换策略.
 */
public abstract class CountRolloverStrategy implements WindowRolloverStrategy<CountWindowState> {

    @Override
    public final boolean shouldRollover(CountWindowState currentWindow, long nowNs, WindowConfig<CountWindowState> config) {
        return currentWindow.requested() >= config.getRequestCycle();
    }

    @Override
    public final CountWindowState createWindowState(long nowNs) {
        return new CountWindowState();
    }
}
