package io.github.workload.overloading;

import io.github.workload.annotations.NotThreadSafe;
import io.github.workload.annotations.ThreadSafe;
import io.github.workload.annotations.VisibleForTesting;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.Iterator;
import java.util.Map;
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

    private volatile AdmissionLevel admissionLevel = AdmissionLevel.ofAdmitAll();
    protected TumblingSampleWindow window;
    protected final String name;
    @VisibleForTesting
    final WorkloadSheddingPolicy policy = new WorkloadSheddingPolicy();
    protected AtomicBoolean windowSwapLock = new AtomicBoolean(false);

    protected abstract boolean isOverloaded(long nowNs);

    protected WorkloadShedder(String name) {
        this.name = name;
        this.window = new TumblingSampleWindow(System.nanoTime(), name);
    }

    @ThreadSafe
    boolean admit(@NonNull WorkloadPriority workloadPriority) {
        boolean admitted = admissionLevel.admit(workloadPriority);
        advanceWindow(System.nanoTime(), workloadPriority, admitted);
        return admitted;
    }

    @VisibleForTesting
    AdmissionLevel admissionLevel() {
        return admissionLevel;
    }

    @ThreadSafe
    private void advanceWindow(long nowNs, WorkloadPriority workloadPriority, boolean admitted) {
        if (window.full(nowNs)) {
            if (!windowSwapLock.compareAndSet(false, true)) {
                return;
            }

            try {
                swapWindow(nowNs);
            } finally {
                windowSwapLock.set(false);
            }
        } else {
            // 窗口切换期间，并发的请求不采样
            window.sample(workloadPriority, admitted);
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
    @VisibleForTesting
    void adaptAdmissionLevel(boolean overloaded) {
        if (overloaded) {
            dropMore();
        } else {
            admitMore();
        }
    }

    private void dropMore() {
        // 如果上个周期的准入请求非常少，那么 expectedDropNextCycle 可能为0
        final int expectedDropNextCycle = (int) (policy.getDropRate() * window.admitted());
        final ConcurrentSkipListMap<Integer, AtomicInteger> histogram = window.histogram();
        int accumulatedDrop = 0;
        final Iterator<Integer> descendingP = histogram.headMap(admissionLevel.P(), true).descendingKeySet().iterator();
        while (descendingP.hasNext()) {
            // TODO inclusive logic
            final int P = descendingP.next();
            final int admittedLastCycle = histogram.get(P).get();
            accumulatedDrop += admittedLastCycle;
            if (log.isDebugEnabled()) {
                log.debug("[{}] drop plan(P:{} admitted:{}), last window admitted:{}, accumulated:{}/{}",
                        name, P, admittedLastCycle, window.admitted(), accumulatedDrop, expectedDropNextCycle);
            }

            if (accumulatedDrop >= expectedDropNextCycle) {
                // TODO if expectedDropNextCycle is 0? drop the admitted lowest priority
                // FIXME level 应该是下一个 level
                // FIXME AdmissionLevel(B=20,U=122;P=5242) -> WorkloadPriority(B=20, U=122, P=5242)
                if (accumulatedDrop - expectedDropNextCycle > 100) {
                    // 抛弃太多了，可能误伤
                }
                final WorkloadPriority target = WorkloadPriority.fromP(P);
                log.warn("[{}] dropping more({}/{}), last window admitted:{}, {} -> {}", name, accumulatedDrop, expectedDropNextCycle, window.admitted(), admissionLevel, target);
                admissionLevel = admissionLevel.changeTo(target);
                return;
            }

            if (!descendingP.hasNext()) {
                // head of histogram, we should never drop all
                log.warn("[{}] HEAD ALREADY", name);
            }
        }

        // already at head of histogram
        log.warn("[{}] already head", name);
        // TODO edge case，还不够扣呢
    }

    private void admitMore() {
        if (ADMIT_ALL_P == admissionLevel.P()) {
            return;
        }

        final int expectedAddNextCycle = (int) (policy.getRecoverRate() * window.admitted());
        int accumulatedAdd = 0;
        // entrySet is in ascending order
        final Iterator<Map.Entry<Integer, AtomicInteger>> ascendingP = window.histogram().tailMap(admissionLevel.P()).entrySet().iterator();
        while (ascendingP.hasNext()) {
            final Map.Entry<Integer, AtomicInteger> entry = ascendingP.next();
            final int P = entry.getKey();
            final int droppedLastCycle = entry.getValue().get();
            accumulatedAdd += droppedLastCycle;
            log.debug("[{}] admit plan(P:{} dropped:{}), accumulated:{}/{}", name, P, droppedLastCycle, accumulatedAdd, expectedAddNextCycle);

            if (accumulatedAdd >= expectedAddNextCycle) {
                final WorkloadPriority target = WorkloadPriority.fromP(P);
                log.warn("[{}] admitting more({}/{}), {} -> {}", name, accumulatedAdd, expectedAddNextCycle, admissionLevel, target);
                admissionLevel = admissionLevel.changeTo(target);
                return;
            }

            if (!ascendingP.hasNext()) {
                log.warn("[{}] tail reached but still not enough for admit more: happy to admit all", name);
                admissionLevel = AdmissionLevel.ofAdmitAll();
                return;
            }
        }

        // already at tail of histogram
        log.debug("[{}] already at histogram tail", name);
    }

}
