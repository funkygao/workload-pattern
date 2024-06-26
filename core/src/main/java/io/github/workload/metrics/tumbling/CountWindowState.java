package io.github.workload.metrics.tumbling;

import io.github.workload.WorkloadPriority;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class CountWindowState extends WindowState {
    private Map<Integer /* priority */, AtomicInteger /* 最近窗口内累计请求数量 */> histogram = new ConcurrentHashMap<>();

    CountWindowState() {
        super();
    }

    public Map<Integer, AtomicInteger> histogram() {
        return histogram;
    }

    @Override
    protected void doSample(WorkloadPriority priority, boolean admitted) {
        AtomicInteger prioritizedCounter = histogram.computeIfAbsent(priority.B(), key -> new AtomicInteger(0));
        prioritizedCounter.incrementAndGet();
    }

    @Override
    protected void cleanup() {
        histogram.clear();
    }

    @Override
    protected void logRollover(String prefix, long nowNs, WindowState nextWindow, WindowConfig config) {
        log.debug("[{}] swapped window:{} -> {}, requested:{}, delta:{}",
                prefix,
                this.hashCode(), nextWindow.hashCode(),
                this.requested(), this.requested() - config.getRequestCycle());
    }

    @Override
    protected synchronized void resetForTesting() {
        super.resetForTesting();
        histogram.clear();
    }
}
