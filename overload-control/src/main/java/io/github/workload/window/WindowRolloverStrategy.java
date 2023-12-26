package io.github.workload.window;

/**
 * 允许外部实现不同的窗口滚动逻辑.
 */
public interface WindowRolloverStrategy {

    /**
     *
     * @param currentWindow
     * @param nowNs {@link System#nanoTime()}
     * @param config
     * @return
     */
    boolean shouldRollover(WindowState currentWindow, long nowNs, WindowConfig config);
}
