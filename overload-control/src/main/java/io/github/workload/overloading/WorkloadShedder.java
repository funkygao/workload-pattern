package io.github.workload.overloading;

import io.github.workload.annotations.ThreadSafe;
import io.github.workload.annotations.VisibleForTesting;
import io.github.workload.window.TimeAndCountRolloverStrategy;
import io.github.workload.window.TimeAndCountWindowState;
import io.github.workload.window.TumblingWindow;
import io.github.workload.window.WindowConfig;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * How to shed excess workload based on {@link WorkloadPriority}.
 */
@Slf4j
@ThreadSafe
abstract class WorkloadShedder {
    private static final int ADMIT_ALL_P = AdmissionLevel.ofAdmitAll().P();

    protected final String name;
    private volatile AdmissionLevel admissionLevel = AdmissionLevel.ofAdmitAll();

    protected final TumblingWindow<TimeAndCountWindowState> window;

    @VisibleForTesting
    final WorkloadSheddingPolicy policy = new WorkloadSheddingPolicy();

    protected abstract boolean isOverloaded(long nowNs, TimeAndCountWindowState windowState);

    protected WorkloadShedder(String name) {
        this.name = name;
        WindowConfig config = new WindowConfig<TimeAndCountWindowState>(
                new TimeAndCountRolloverStrategy(),
                (nowNs, lastWindow) -> {
                    boolean overloaded = isOverloaded(nowNs, lastWindow);
                    adaptAdmissionLevel(overloaded, lastWindow);
                });
        this.window = new TumblingWindow(System.nanoTime(), name, config);
    }

    @ThreadSafe
    boolean admit(@NonNull WorkloadPriority workloadPriority) {
        boolean admitted = admissionLevel.admit(workloadPriority);
        final long nowNs = System.nanoTime();
        window.advance(workloadPriority, admitted, nowNs);
        return admitted;
    }

    @VisibleForTesting
    AdmissionLevel admissionLevel() {
        return admissionLevel;
    }

    @VisibleForTesting
    void adaptAdmissionLevel(boolean overloaded, TimeAndCountWindowState windowState) {
        if (overloaded) {
            dropMore(windowState);
        } else {
            admitMore(windowState);
        }
    }

    private void dropMore(TimeAndCountWindowState windowState) {
        // 如果上个周期的准入请求非常少，那么 expectedDropNextCycle 可能为0
        final int admitted = windowState.admitted();
        final int expectedDropNextCycle = (int) (policy.getDropRate() * admitted);
        final ConcurrentSkipListMap<Integer, AtomicInteger> histogram = windowState.histogram();
        int accumulatedDrop = 0;
        final Iterator<Integer> descendingP = histogram.headMap(admissionLevel.P(), true).descendingKeySet().iterator();
        while (descendingP.hasNext()) {
            // TODO inclusive logic
            final int P = descendingP.next();
            final int admittedLastCycle = histogram.get(P).get();
            accumulatedDrop += admittedLastCycle;
            if (log.isDebugEnabled()) {
                log.debug("[{}] drop plan(P:{} admitted:{}), last window admitted:{}, accumulated:{}/{}",
                        name, P, admittedLastCycle, admitted, accumulatedDrop, expectedDropNextCycle);
            }

            if (accumulatedDrop >= expectedDropNextCycle) {
                // TODO if expectedDropNextCycle is 0? drop the admitted lowest priority
                // FIXME level 应该是下一个 level
                // FIXME AdmissionLevel(B=20,U=122;P=5242) -> WorkloadPriority(B=20, U=122, P=5242)
                if (accumulatedDrop - expectedDropNextCycle > 100) {
                    // 抛弃太多了，可能误伤
                }
                final WorkloadPriority target = WorkloadPriority.fromP(P);
                log.warn("[{}] dropping more({}/{}), last window admitted:{}, {} -> {}", name, accumulatedDrop, expectedDropNextCycle, admitted, admissionLevel, target);
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

    private void admitMore(TimeAndCountWindowState windowState) {
        if (ADMIT_ALL_P == admissionLevel.P()) {
            return;
        }

        final int admitted = windowState.admitted();
        final int expectedAddNextCycle = (int) (policy.getRecoverRate() * admitted);
        int accumulatedAdd = 0;
        // entrySet is in ascending order
        final Iterator<Map.Entry<Integer, AtomicInteger>> ascendingP = windowState.histogram().tailMap(admissionLevel.P()).entrySet().iterator();
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
