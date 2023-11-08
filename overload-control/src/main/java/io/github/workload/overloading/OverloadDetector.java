package io.github.workload.overloading;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 基于(数量，时间)窗口的过载检测.
 *
 * <p>复合型窗口设计有助于及时进行过载检测和准入控制调整.</p>
 * <p>avg waiting time of requests in the pending queue (or queuing time for short) to to profile the load status of a server</p>
 * <p>{@code queuing time = T(request processing started) - T(request arrival)}</p>
 * <p>它相当于amazon管理中的output metrics，而如下input metrics最终都会反映到这个结果：</p>
 * <p>吞吐率/latency/CPU Utilization/packet rate/number of pending requests/request processing time</p>
 * <p>A -> B，B处理非常慢，queuing time of A能够更准确地反映A的load status，而response time无法反映A的load status.</p>
 * <p>为什么不用CPU使用率度量load status？High load does not necessarily infer overload.</p>
 * <p>As long as the server can handle requests in a timely manner (e.g., as reflected by the low queuing time), it is not considered to be overloaded, even if its CPU utilization stays high.</p>
 */
@Slf4j
class OverloadDetector {
    private static final long WindowTimeSizeNs = 1000_000_000; // 基于时间的窗口：1s
    private static final int WindowRequests = 500; // 基于请求数量的窗口：微信配置为2K

    // 当前窗口开始时间点
    private volatile long windowStartNs = System.nanoTime();

    // 当前窗口的请求总量
    private AtomicInteger requestCounter = new AtomicInteger(0);

    // 当前窗口的准入请求总量
    private AtomicInteger admittedCounter = new AtomicInteger(0);

    volatile long overloadQueuingMs;
    volatile long overloadedAtNs = 0;
    WindowSlideHook hook;

    /**
     * 当前窗口各种优先级请求的数量分布.
     *
     * <p>key is {@link WorkloadPriority#P()}.</p>
     */
    private ConcurrentSkipListMap<Integer, AtomicInteger> histogram = new ConcurrentSkipListMap<>();

    private AtomicLong accumulatedWaitNs = new AtomicLong(0); // 该窗口内所有请求总排队时长

    private AtomicBoolean slidingWindowLock = new AtomicBoolean(false);

    // 当前准入等级
    private AdmissionLevel localAdmissionLevel = AdmissionLevel.ofAdmitAll();

    private double dropRate = 0.05; // 5%
    private double recoverRate = 0.01; // 1%

    OverloadDetector(long overloadQueuingMs) {
        this.overloadQueuingMs = overloadQueuingMs;
    }

    boolean admit(@NonNull WorkloadPriority workloadPriority) {
        advanceWindow(workloadPriority);

        boolean admitted = localAdmissionLevel.admit(workloadPriority);
        if (admitted) {
            admittedCounter.incrementAndGet();
        }
        return admitted;
    }

    private void advanceWindow(WorkloadPriority workloadPriority) {
        if (slidingWindowLock.get()) {
            return;
        }

        int requests = requestCounter.incrementAndGet();
        updateHistogram(workloadPriority);
        long nowNs = System.nanoTime();
        if (nowNs - windowStartNs > WindowTimeSizeNs // 时间满足
                || requests > WindowRequests) { // 请求数量满足
            slideWindow(nowNs);
        }
    }

    private void updateHistogram(WorkloadPriority workloadPriority) {
        histogramCounter(workloadPriority).incrementAndGet();
    }

    boolean isOverloaded(long nowNs) {
        return avgQueuingTimeMs() > overloadQueuingMs // 排队时间长
                || (nowNs - overloadedAtNs) <= WindowTimeSizeNs; // 距离上次显式过载仍在窗口期
    }

    private void slideWindow(long nowNs) {
        if (!slidingWindowLock.compareAndSet(false, true)) {
            // single flight
            return;
        }

        try {
            // 调整准入级别，每个窗口周期一次
            updateAdmissionLevel(isOverloaded(nowNs));

            windowStartNs = nowNs;
            requestCounter.set(0);
            admittedCounter.set(0);
            accumulatedWaitNs.set(0);
            histogram.clear();

            if (hook != null) {
                try {
                    hook.onSlidingWindow();
                } catch (Throwable ignored) {
                    log.error("Hook error ignored on purpose", ignored);
                }
            }
        } finally {
            slidingWindowLock.set(false);
        }
    }

    private AtomicInteger histogramCounter(WorkloadPriority workloadPriority) {
        int key = workloadPriority.P();
        AtomicInteger counter = histogram.get(key);
        if (counter == null) {
            counter = new AtomicInteger(0);
            AtomicInteger counter2 = histogram.putIfAbsent(key, counter);
            if (counter2 != null) {
                counter = counter2;
            }
        }

        return counter;
    }

    // 调整策略：把下一个窗口的准入请求量控制到目标值，从而滑动准入等级游标
    // 根据当前是否过载，计算下一个窗口准入量目标值
    private void updateAdmissionLevel(boolean overloaded) {
        int admitN = admittedCounter.get();
        int currentP = localAdmissionLevel.P();
        // 类似TCP拥塞控制AIMD的反馈控制算法：快速下降，慢速上升
        if (overloaded) {
            // 把下一个窗口的 admitted requests 下降到当前窗口的 (1 - dropRate)%
            int expectedN = (int) (1 - dropRate) * admitN;
            // 当前窗口准入请求=100，下一个窗口准入=95，当前P=14
            // 当前窗口histogram：{2:3, 3:1, 8:20, 14*:3, 20:40}
            // 调整过程：descendingKeySet => [14, 8, 3, 2]，对应的counter：[3, 20, 1, 3]
            // 100 - 3 = 97
            // 97 - 20 = 77 < 95, P=8：准入等级P由14调整到8
            for (Integer P : histogram.headMap(currentP, true).descendingKeySet()) { // 优先级越高在keySet越靠前
                admitN -= histogram.get(P).intValue();
                if (admitN <= expectedN) {
                    log.warn("load shedding, switched P {} -> {}", currentP, P);
                    localAdmissionLevel.changeTo(WorkloadPriority.fromP(P));
                    return;
                }
            }
            // TODO edge case，还不够扣呢
        } else {
            // 把下一个窗口的 admitted requests 提升到当前窗口的 (1 + recoverRate)%
            int expectedN = (int) (1 + recoverRate) * admitN;
            // 如果当前P已经在histogram最尾部，则不进入循环：啥都不做，无需调整
            for (Integer P : histogram.tailMap(currentP, false).keySet()) {
                admitN += histogram.get(P).intValue();
                if (admitN >= expectedN) {
                    log.warn("load recovering, switched P {} -> {}", currentP, P);
                    localAdmissionLevel.changeTo(WorkloadPriority.fromP(P));
                    return;
                }
            }
        }
    }

    void addWaitingNs(long waitingNs) {
        if (slidingWindowLock.get()) {
            // 正在滑动窗口，即使计数也可能被重置
            return;
        }

        accumulatedWaitNs.addAndGet(waitingNs);
    }

    long avgQueuingTimeMs() {
        int requests = requestCounter.get();
        if (requests == 0) {
            // avoid divide by zero
            return 0;
        }

        return accumulatedWaitNs.get() / (requests * 1000_000);
    }

}
