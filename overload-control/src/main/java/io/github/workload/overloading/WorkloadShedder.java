package io.github.workload.overloading;

import io.github.workload.annotations.ThreadSafe;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * How to shed excess workload based on {@link WorkloadPriority}.
 */
@Slf4j
@ThreadSafe
abstract class WorkloadShedder {
    private final AdmissionLevel admissionLevel = AdmissionLevel.ofAdmitAll();
    protected final MetricsRollingWindow window = new MetricsRollingWindow(System.nanoTime());
    private final WorkloadSheddingPolicy policy = new WorkloadSheddingPolicy();
    private ConcurrentSkipListMap<Integer, AtomicInteger> prioritizedRequestCounters = new ConcurrentSkipListMap<>();
    protected AtomicBoolean swapLock = new AtomicBoolean(false);

    protected abstract boolean isOverloaded(long nowNs);

    @ThreadSafe
    boolean admit(@NonNull WorkloadPriority workloadPriority) {
        // 当前准入等级判断
        boolean admitted = admissionLevel.admit(workloadPriority);
        // 采样到窗口
        advanceWindow(System.nanoTime(), workloadPriority, admitted);
        return admitted;
    }

    @ThreadSafe
    private void advanceWindow(long nowNs, WorkloadPriority workloadPriority, boolean admitted) {
        if (swapLock.get()) {
            // 切换窗口期间进入的请求暂时不处理：切换窗口是很快的，对过载保护没实质性影响
            // TODO put into queue, after window swapped, dump to current window at once
            return;
        }

        incrementRequestCounterFor(workloadPriority);
        window.tick(admitted);
        if (window.full(nowNs)) {
            swapWindow(nowNs);
        }
    }

    private void incrementRequestCounterFor(WorkloadPriority workloadPriority) {
        AtomicInteger counter = prioritizedRequestCounters.computeIfAbsent(workloadPriority.P(), key -> {
            log.debug("histogram register new P: {}", key);
            return new AtomicInteger(0);
        });
        counter.incrementAndGet();
    }

    private void swapWindow(long nowNs) {
        if (!swapLock.compareAndSet(false, true)) {
            return;
        }

        try {
            // 根据当前窗口信息和请求分布决定下个周期的准入等级
            updateAdmissionLevel(isOverloaded(nowNs));

            // 当前窗口和请求分布数据已经使用完毕
            window.restart(nowNs);
            prioritizedRequestCounters.clear();
        } finally {
            swapLock.set(false);
        }
    }



    // 调整策略：把下一个窗口的准入请求量控制到目标值，从而滑动准入等级游标
    // 根据当前是否过载，计算下一个窗口准入量目标值
    // 服务器上维护者目前准入优先级下，过去一个周期的每个优先级的请求量
    // 当过载时，通过消减下一个周期的请求量来减轻负载
    private void updateAdmissionLevel(boolean overloaded) {
        int admitN = window.admitted();
        int currentP = admissionLevel.P();
        // 类似TCP拥塞控制AIMD的反馈控制算法：快速下降，慢速上升
        if (overloaded) {
            // 把下一个窗口的 admitted requests 下降到当前窗口的 (1 - dropRate)%
            int expectedN = (int) (1 - policy.getDropRate()) * admitN;
            // 当前窗口准入请求=100，下一个窗口准入=95，当前P=14
            // 当前窗口histogram：{2:3, 3:1, 8:20, 14*:3, 20:40}
            // 调整过程：descendingKeySet => [14, 8, 3, 2]，对应的counter：[3, 20, 1, 3]
            // 100 - 3 = 97
            // 97 - 20 = 77 < 95, P=8：准入等级P由14调整到8
            for (Integer P : prioritizedRequestCounters.headMap(currentP, true).descendingKeySet()) { // 优先级越高在keySet越靠前
                admitN -= prioritizedRequestCounters.get(P).intValue();
                if (admitN <= expectedN) {
                    // TODO avoid sudden drop, sensitivity，而且至少要保证50%
                    log.warn("load shedding, switched P {} -> {}", currentP, P);
                    admissionLevel.changeTo(WorkloadPriority.fromP(P));
                    return;
                }
            }
            // TODO edge case，还不够扣呢
        } else {
            // 把下一个窗口的 admitted requests 提升到当前窗口的 (1 + recoverRate)%
            int expectedN = (int) (1 + policy.getRecoverRate()) * admitN;
            // 如果当前P已经在histogram最尾部，则不进入循环：啥都不做，无需调整
            for (Integer P : prioritizedRequestCounters.tailMap(currentP, false).keySet()) {
                admitN += prioritizedRequestCounters.get(P).intValue();
                if (admitN >= expectedN) {
                    log.warn("load recovering, switched P {} -> {}", currentP, P);
                    admissionLevel.changeTo(WorkloadPriority.fromP(P));
                    return;
                }
            }
        }
    }

}
