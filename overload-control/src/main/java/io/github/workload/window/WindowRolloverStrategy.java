package io.github.workload.window;

/**
 * 窗口滚动策略.
 *
 * @param <S> 具体的窗口状态
 */
public interface WindowRolloverStrategy<S extends WindowState> {

    /**
     * 根据{@link WindowConfig}决定当前窗口是否应该滚动.
     *
     * @param currentWindow 当前窗口
     * @param nowNs 当前系统时间，see {@link System#nanoTime()}
     * @param config 窗口配置
     * @return true if should rollover the current window to next window
     */
    boolean shouldRollover(S currentWindow, long nowNs, WindowConfig<S> config);

    /**
     * 创建新窗口状态.
     *
     * @param nowNs current hw clock
     * @return a new window state
     */
    S createWindowState(long nowNs);
}
