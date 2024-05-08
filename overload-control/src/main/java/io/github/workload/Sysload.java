package io.github.workload;

/**
 * 系统负载指标提供者.
 */
public interface Sysload {

    /**
     * 最近的CPU利用率，[0.0, 1.0].
     *
     * <p>是指程序的CPU占用时间除以程序的运行时间</p>
     */
    double cpuUsage();
}
