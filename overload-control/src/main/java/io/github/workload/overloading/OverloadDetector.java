package io.github.workload.overloading;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

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
    SlidingWindow window;
    WindowSlideHook hook;
    double dropRate = 0.05; // 5%
    double recoverRate = 0.01; // 1%

    private AtomicBoolean slideLock = new AtomicBoolean(false);

    /**
     * 当前准入等级.
     */
    private AdmissionLevel admissionLevel = AdmissionLevel.ofAdmitAll();

    /**
     * 配置：平均排队时长多大被认为过载.
     *
     * <p>由于是配置，没有加{@code volatile}</p>
     */
    long overloadQueuingMs;

    /**
     * 最近一次显式过载的时间.
     */
    volatile long overloadedAtNs = 0;

    /**
     * 当前窗口各种优先级请求的数量分布.
     *
     * <p>key is {@link WorkloadPriority#P()}.</p>
     * <p>它被用来调整新窗口的{@link AdmissionLevel}.</p>
     */
    private ConcurrentSkipListMap<Integer, AtomicInteger> histogram = new ConcurrentSkipListMap<>();

    OverloadDetector(long overloadQueuingMs) {
        this(overloadQueuingMs, SlidingWindow.DefaultTimeCycleNs, SlidingWindow.DefaultRequestCycle);
    }

    OverloadDetector(long overloadQueuingMs, long timeCycleNs, int requestCycle) {
        this.overloadQueuingMs = overloadQueuingMs;
        this.window = new SlidingWindow(timeCycleNs, requestCycle);
    }

    boolean admit(@NonNull WorkloadPriority workloadPriority) {
        boolean admitted = admissionLevel.admit(workloadPriority);
        advanceWindow(System.nanoTime(), workloadPriority, admitted);
        return admitted;
    }

    private void advanceWindow(long nowNs, WorkloadPriority workloadPriority, boolean admitted) {
        if (slideLock.get()) {
            return;
        }

        window.tick(admitted);
        updateHistogram(workloadPriority);
        if (window.full(nowNs)) {
            slideWindow(nowNs);
        }
    }

    private void updateHistogram(WorkloadPriority workloadPriority) {
        histogramCounter(workloadPriority).incrementAndGet();
    }

    boolean isOverloaded(long nowNs) {
        return window.avgQueuedMs() > overloadQueuingMs // 排队时间长
                || (nowNs - overloadedAtNs) <= window.getTimeCycleNs(); // 距离上次显式过载仍在窗口期
    }

    private void slideWindow(long nowNs) {
        if (!slideLock.compareAndSet(false, true)) {
            // single flight
            return;
        }

        try {
            // 调整准入级别，每个窗口周期一次
            updateAdmissionLevel(isOverloaded(nowNs));

            window.slide(nowNs);
            histogram.clear();

            if (hook != null) {
                try {
                    hook.onSlidingWindow();
                } catch (Throwable ignored) {
                    log.error("Hook error ignored on purpose", ignored);
                }
            }
        } finally {
            slideLock.set(false);
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
        int admitN = window.admitted();
        int currentP = admissionLevel.P();
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
                    admissionLevel.changeTo(WorkloadPriority.fromP(P));
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
                    admissionLevel.changeTo(WorkloadPriority.fromP(P));
                    return;
                }
            }
        }
    }

    void addWaitingNs(long waitingNs) {
        if (slideLock.get()) {
            // 正在滑动窗口，即使计数也可能被重置
            return;
        }

        window.addWaitingNs(waitingNs);
    }

}
