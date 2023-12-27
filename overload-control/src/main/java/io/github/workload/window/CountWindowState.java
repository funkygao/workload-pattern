package io.github.workload.window;

import io.github.workload.overloading.WorkloadPriority;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class CountWindowState extends WindowState {
    private Map<Integer /* priority */, AtomicInteger /* 最近窗口内累计发送消息量 */> throughputHistogram = new ConcurrentHashMap<>();

    @Override
    protected void doSample(WorkloadPriority priority) {

    }

    @Override
    protected void doSample(WorkloadPriority priority, boolean admitted) {

    }

    @Override
    void cleanup() {

    }
}
