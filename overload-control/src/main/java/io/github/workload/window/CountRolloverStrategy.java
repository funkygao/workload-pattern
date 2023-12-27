package io.github.workload.window;

public class CountRolloverStrategy implements WindowRolloverStrategy<CountWindowState> {

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean shouldRollover(CountWindowState currentWindow, long nowNs, WindowConfig config) {
        return currentWindow.requested() >= config.getRequestCycle();
    }
}
