package io.github.workload.overloading;

import io.github.workload.annotations.ThreadSafe;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * How to shed excess workload.
 */
@Slf4j
@ThreadSafe
abstract class WorkloadShedder {

    /**
     * 当前准入等级.
     *
     * <p>每个{@link WorkloadShedder}都有自己的准入等级，互不冲突，各管各的.</p>
     * <p>全局的基于CPU负载的{@link WorkloadShedder}在CPU过载时，它的准入等级开始接管准入判断逻辑.</p>
     */
    private final AdmissionLevel admissionLevel = AdmissionLevel.ofAdmitAll();

    MetricsRollingWindow window;

    /**
     * 降速因子.
     */
    double dropRate = 0.05; // 5%

    /**
     * 提速因子.
     *
     * <p>(加速下降/reject request，慢速恢复/admit request)</p>
     * <p>相当于冷却周期，如果没有它会造成负载短时间下降造成大量请求被放行，严重时打满CPU</p>
     */
    double recoverRate = 0.01; // 1%

    protected AtomicBoolean slideLock = new AtomicBoolean(false);

    /**
     * 当前窗口各种优先级请求(包括admitted)的数量分布.
     *
     * <p>key is {@link WorkloadPriority#P()}.</p>
     * <p>它被用来调整新窗口的{@link AdmissionLevel}.</p>
     */
    private ConcurrentSkipListMap<Integer, AtomicInteger> histogram = new ConcurrentSkipListMap<>();

    protected abstract boolean isOverloaded(long nowNs);

    WorkloadShedder() {
        window = new MetricsRollingWindow(MetricsRollingWindow.DEFAULT_TIME_CYCLE_NS,
                MetricsRollingWindow.DEFAULT_REQUEST_CYCLE);
    }

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
        if (slideLock.get()) {
            return;
        }

        window.tick(admitted);
        // 该优先级请求总量
        updateHistogram(workloadPriority);
        if (window.full(nowNs)) {
            slideWindow(nowNs);
        }
    }

    private void updateHistogram(WorkloadPriority workloadPriority) {
        histogramCounter(workloadPriority).incrementAndGet();
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
        } finally {
            slideLock.set(false);
        }
    }

    private AtomicInteger histogramCounter(WorkloadPriority workloadPriority) {
        int key = workloadPriority.P();
        AtomicInteger counter = histogram.get(key);
        if (counter != null) {
            return counter;
        }
        return histogram.computeIfAbsent(key, P -> {
            log.debug("histogram register new P: {}", P);
            return new AtomicInteger(0);
        });
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
            int expectedN = (int) (1 - dropRate) * admitN;
            // 当前窗口准入请求=100，下一个窗口准入=95，当前P=14
            // 当前窗口histogram：{2:3, 3:1, 8:20, 14*:3, 20:40}
            // 调整过程：descendingKeySet => [14, 8, 3, 2]，对应的counter：[3, 20, 1, 3]
            // 100 - 3 = 97
            // 97 - 20 = 77 < 95, P=8：准入等级P由14调整到8
            for (Integer P : histogram.headMap(currentP, true).descendingKeySet()) { // 优先级越高在keySet越靠前
                admitN -= histogram.get(P).intValue();
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

}
