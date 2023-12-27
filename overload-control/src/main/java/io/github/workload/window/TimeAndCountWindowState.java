package io.github.workload.window;

import io.github.workload.overloading.WorkloadPriority;
import lombok.Getter;

import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

import static io.github.workload.window.WindowConfig.NS_PER_MS;

public class TimeAndCountWindowState extends WindowState {
    @Getter
    private final long startNs;
    private final LongAdder admittedCounter;
    private final LongAdder accumulatedQueuedNs;
    private final ConcurrentSkipListMap<Integer /* priority */, AtomicInteger /* requested */> histogram;

    TimeAndCountWindowState(long startNs) {
        super();
        this.startNs = startNs;
        this.admittedCounter = new LongAdder();
        this.accumulatedQueuedNs = new LongAdder();
        this.histogram = new ConcurrentSkipListMap<>();
    }

    public ConcurrentSkipListMap<Integer, AtomicInteger> histogram() {
        return histogram;
    }

    @Override
    protected void doSample(WorkloadPriority priority) {
    }

    public int admitted() {
        return admittedCounter.intValue();
    }

    @Override
    protected void doSample(WorkloadPriority priority, boolean admitted) {
        if (admitted) {
            admittedCounter.increment();
        }
        AtomicInteger prioritizedCounter = histogram.computeIfAbsent(priority.P(), key -> new AtomicInteger(0));
        prioritizedCounter.incrementAndGet();
    }

    @Override
    void cleanup() {
        histogram.clear();
    }

    public void waitNs(long waitingNs) {
        accumulatedQueuedNs.add(waitingNs);
    }

    long ageMs(long nowNs) {
        return (nowNs - startNs) / NS_PER_MS;
    }

    public long avgQueuedMs() {
        int requested = requested();
        if (requested == 0) {
            // avoid divide by zero
            return 0;
        }

        return accumulatedQueuedNs.longValue() / (requested * NS_PER_MS);
    }
}
