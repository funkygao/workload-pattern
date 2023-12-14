package io.github.workload.overloading;

import io.github.workload.annotations.NotThreadSafe;
import io.github.workload.annotations.ThreadSafe;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.Iterator;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * How to shed excess workload based on {@link WorkloadPriority}.
 */
@Slf4j
@ThreadSafe
abstract class WorkloadShedder {
    private static final int ADMIT_ALL_P = AdmissionLevel.ofAdmitAll().P();

    @Getter(AccessLevel.PACKAGE)
    private final AdmissionLevel admissionLevel = AdmissionLevel.ofAdmitAll();
    protected SamplingWindow window;
    protected final String name;
    private final WorkloadSheddingPolicy policy = new WorkloadSheddingPolicy();
    protected AtomicBoolean windowSwapLock = new AtomicBoolean(false);

    protected abstract boolean isOverloaded(long nowNs);

    protected WorkloadShedder(String name) {
        this.name = name;
        this.window = new SamplingWindow(System.nanoTime(), name);
    }

    @ThreadSafe
    boolean admit(@NonNull WorkloadPriority workloadPriority) {
        boolean admitted = admissionLevel.admit(workloadPriority);
        advanceWindow(System.nanoTime(), workloadPriority, admitted);
        return admitted;
    }

    @ThreadSafe
    private void advanceWindow(long nowNs, WorkloadPriority workloadPriority, boolean admitted) {
        window.sample(workloadPriority, admitted);
        if (window.full(nowNs)) {
            if (!windowSwapLock.compareAndSet(false, true)) {
                return;
            }

            try {
                log.trace("swap window ...");
                swapWindow(nowNs);
            } finally {
                windowSwapLock.set(false);
            }
        }
    }

    @NotThreadSafe(serial = true)
    private void swapWindow(long nowNs) {
        // 当前窗口 => 下个窗口的准入等级
        adaptAdmissionLevel(isOverloaded(nowNs));

        // 当前窗口数据已经使用完毕
        // 并发情况下可能会丢失一部分采样数据，acceptable for now
        window.restart(nowNs);
    }

    // 调整策略：把下一个窗口的准入请求量控制到目标值，从而滑动准入等级游标
    // 根据当前是否过载，计算下一个窗口准入量目标值
    // 服务器上维护者目前准入优先级下，过去一个周期的每个优先级的请求量
    // 当过载时，通过消减下一个周期的请求量来减轻负载
    @NotThreadSafe(serial = true)
    private void adaptAdmissionLevel(boolean overloaded) {
        log.trace("[{}] {}, {}", name, window, admissionLevel);
        if (overloaded) {
            dropMore();
        } else {
            admitMore();
        }
    }

    private void dropMore() {
        int admittedLastCycle = window.admitted(); // 上个窗口
        // 把下一个窗口的 admitted requests 下降到当前窗口的 (1 - dropRate)%
        int toAdmitNextCycle = (int) (1 - policy.getDropRate()) * admittedLastCycle;
        // 当前窗口准入请求=100，下一个窗口准入=95，当前P=14
        // 当前窗口histogram：{2:3, 3:1, 8:20, 14*:3, 20:40}
        // 调整过程：descendingKeySet => [14, 8, 3, 2]，对应的counter：[3, 20, 1, 3]
        // 100 - 3 = 97
        // 97 - 20 = 77 < 95, P=8：准入等级P由14调整到8
        ConcurrentSkipListMap<Integer, AtomicInteger> histogram = window.histogram();
        for (Integer P : histogram.headMap(admissionLevel.P(), true).descendingKeySet()) { // 优先级越高在keySet越靠前
            admittedLastCycle -= histogram.get(P).intValue();
            if (admittedLastCycle <= toAdmitNextCycle) {
                // TODO avoid sudden drop, sensitivity，而且至少要保证50%
                admissionLevel.changeTo(WorkloadPriority.fromP(P));
                return;
            }
        }
        // TODO edge case，还不够扣呢
    }

    private void admitMore() {
        if (ADMIT_ALL_P == admissionLevel.P()) {
            log.trace("[{}] cannot admit more", name);
            return;
        }

        final int expectedExtraNextCycle = (int) (policy.getRecoverRate() * window.admitted());
        ConcurrentSkipListMap<Integer, AtomicInteger> histogram = window.histogram();
        int accumulatedExtra = 0;
        Iterator<Integer> iterator = histogram.tailMap(admissionLevel.P()).keySet().iterator();
        while (iterator.hasNext()) {
            Integer P = iterator.next();
            final int droppedLastCycle = histogram.get(P).intValue();
            accumulatedExtra += droppedLastCycle;
            if (accumulatedExtra >= expectedExtraNextCycle) {
                WorkloadPriority target = WorkloadPriority.fromP(P);
                log.warn("[{}] expected extra:{}, {} -> {}", name, expectedExtraNextCycle, admissionLevel, target);
                admissionLevel.changeTo(target);
                return;
            }

            if (!iterator.hasNext()) {
                log.warn("[{}] tail reached but still not enough for admit more: happy to admit all", name);
                admissionLevel.changeTo(WorkloadPriority.ofLowestPriority());
                return;
            }
        }

        // already at tail of histogram
    }

}
