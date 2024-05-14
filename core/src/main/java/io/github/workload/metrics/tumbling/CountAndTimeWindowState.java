package io.github.workload.metrics.tumbling;

import io.github.workload.WorkloadPriority;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

import static io.github.workload.metrics.tumbling.WindowConfig.NS_PER_MS;

@Slf4j
public class CountAndTimeWindowState extends WindowState {
    /**
     * 窗口启动时间.
     * <p>
     * 通过{@link System#nanoTime()}获取.
     */
    @Getter(AccessLevel.PACKAGE)
    private final long startNs;

    /**
     * 被准入的数量.
     */
    private final LongAdder admittedCounter;

    /**
     * 累计排队等待时长.
     */
    private final LongAdder accumulatedQueuedNs;

    private final ConcurrentSkipListMap<Integer /* WorkloadPriority.P */, AtomicInteger /* requested */> histogram;

    CountAndTimeWindowState(long startNs) {
        super();
        this.startNs = startNs;
        this.admittedCounter = new LongAdder();
        this.accumulatedQueuedNs = new LongAdder();
        this.histogram = new ConcurrentSkipListMap<>();
    }

    /**
     * 各个{@link WorkloadPriority#P()}的请求数量分布.
     */
    public ConcurrentSkipListMap<Integer, AtomicInteger> histogram() {
        return histogram;
    }

    /**
     * 窗口期内总计准入多少工作负荷.
     */
    public int admitted() {
        return admittedCounter.intValue();
    }

    /**
     * 窗口期内总计拒绝了多少工作负荷.
     */
    public int shedded() {
        return requested() - admitted();
    }

    public void waitNs(long waitingNs) {
        if (waitingNs > 0) {
            accumulatedQueuedNs.add(waitingNs);
        }
    }

    public long avgQueuedMs() {
        int requested = requested();
        if (requested == 0) {
            // avoid divide by zero
            return 0;
        }

        // 使用long类型确保有效数值范围，并先进行除法以避免精度损失
        long totalQueuedNs = accumulatedQueuedNs.longValue();
        long avgQueuedNs = totalQueuedNs / requested;
        return avgQueuedNs / NS_PER_MS;
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
    protected void cleanup() {
        histogram.clear();
    }

    @Override
    protected void logRollover(String prefix, long nowNs, WindowState nextWindow, WindowConfig config) {
        if (log.isDebugEnabled()) {
            log.debug("[{}] after:{}ms, swap window:{} -> {}, admitted:{}/{}, error:{}",
                    prefix, (nowNs - startNs) / NS_PER_MS,
                    this.hashCode(), nextWindow.hashCode(),
                    this.admitted(), this.requested(),
                    this.requested() - config.getRequestCycle());
        }
    }

    @Override
    protected void resetForTesting() {
        super.resetForTesting();
        histogram.clear();
        this.admittedCounter.reset();
        this.accumulatedQueuedNs.reset();
    }
}
