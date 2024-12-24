package io.github.workload;

/**
 * 超参数.
 *
 * <p>通过JVM配置参数来生效.</p>
 */
public interface HyperParameter {

    /**
     * 滚动窗口的时间长度维度 in ms.
     *
     * <p>默认值：1000</p>
     */
    String WINDOW_TIME_CYCLE_MS = "workload.window.DEFAULT_TIME_CYCLE_MS";

    /**
     * 滚动窗口的请求数量维度.
     *
     * <p>默认值：2K</p>
     */
    String WINDOW_REQUEST_CYCLE = "workload.window.DEFAULT_REQUEST_CYCLE";

    static double getDouble(String key, double defaultValue) {
        String value = System.getProperty(key);
        if (value == null) {
            return defaultValue;
        }

        return Double.parseDouble(value);
    }

    static long getLong(String key, long defaultValue) {
        String value = System.getProperty(key);
        if (value == null) {
            return defaultValue;
        }

        return Long.parseLong(value);
    }

    static int getInt(String key, int defaultValue) {
        return (int) getLong(key, defaultValue);
    }
}
