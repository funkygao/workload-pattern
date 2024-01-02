package io.github.workload.overloading;

import io.github.workload.annotations.ThreadSafe;
import io.github.workload.annotations.VisibleForTesting;
import io.github.workload.window.CountAndTimeRolloverStrategy;
import io.github.workload.window.CountAndTimeWindowState;
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
    private static final double ERROR_RATE_BOUND = 1.0d; // 100%

    protected final String name;
    private volatile AdmissionLevel admissionLevel = AdmissionLevel.ofAdmitAll();

    protected final TumblingWindow<CountAndTimeWindowState> window;

    @VisibleForTesting
    final WorkloadSheddingPolicy policy = new WorkloadSheddingPolicy();

    protected abstract boolean isOverloaded(long nowNs, CountAndTimeWindowState windowState);

    protected WorkloadShedder(String name) {
        this.name = name;
        WindowConfig<CountAndTimeWindowState> config = WindowConfig.create(
                new CountAndTimeRolloverStrategy(),
                (nowNs, lastWindow) -> adaptAdmissionLevel(isOverloaded(nowNs, lastWindow), lastWindow)
        );
        this.window = new TumblingWindow(config, name, System.nanoTime());
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
    void adaptAdmissionLevel(boolean overloaded, CountAndTimeWindowState lastWindow) {
        if (overloaded) {
            dropMore(lastWindow);
        } else {
            admitMore(lastWindow);
        }
    }

    private void dropMore(CountAndTimeWindowState lastWindow) {
        final int admitted = lastWindow.admitted();
        final int expectedToDrop = (int) (policy.getDropRate() * admitted);
        if (expectedToDrop == 0) {
            // 上个周期的准入量太少，无法决策抛弃哪个
            log.info("[{}] unable to drop more: too few window admitted {}", admitted);
            return;
        }

        final int currentP = admissionLevel.P();
        final ConcurrentSkipListMap<Integer, AtomicInteger> histogram = lastWindow.histogram();
        int accumulatedToDrop = 0;
        // histogram: (6, 3) -> (9, 10) -> ... -> (112, 2) -> (195, 11) -> (1894, 3)
        // expectedDropNextCycle:4, admissionLevel.P可能值: 2/8/1894/1999，它是预测值，histogram是实际值
        // admissionLevel.P=1999/1894, 要切换到112，但195是过度抛弃了，error=(11+3-4)=10
        // TODO should we respect currentP?
        final Iterator<Integer> descendingP = histogram.headMap(currentP, true).descendingKeySet().iterator();
        while (descendingP.hasNext()) {
            // once loop entered, will converge inside the loop
            final int candidateP = descendingP.next();
            final int candidateRequested = histogram.get(candidateP).get();
            accumulatedToDrop += candidateRequested;
            if (log.isDebugEnabled()) {
                log.debug("[{}] drop candidate(P:{} requested:{}), window admitted:{}, accumulated:{}/{}",
                        name, candidateP, candidateRequested, admitted, accumulatedToDrop, expectedToDrop);
            }

            if (accumulatedToDrop >= expectedToDrop) {
                double errorRate = (double) (accumulatedToDrop - expectedToDrop) / expectedToDrop;
                int targetP;
                if (descendingP.hasNext() && errorRate < ERROR_RATE_BOUND) {
                    // 误差率可接受，and candidate is not head
                    targetP = descendingP.next();
                    log.warn("[{}] dropping more({}/{}), error:{}, window admitted:{}, {} -> {}", name, accumulatedToDrop, expectedToDrop, errorRate, admitted, admissionLevel, targetP);
                } else {
                    if (candidateP == currentP) {
                        log.warn("[{}] error:{}, tail P has too many requests, await next cycle to drop", name, errorRate);
                        return;
                    }

                    targetP = candidateP;
                    log.warn("[{}] limited dropping more({}/{}), error:{}, window admitted:{}, {} -> {}", name, accumulatedToDrop, expectedToDrop, errorRate, admitted, admissionLevel, targetP);
                }
                admissionLevel = admissionLevel.switchTo(targetP);
                return;
            }

            if (!descendingP.hasNext()) {
                // 还不够扣呢，但已经没有可扣的了：we should never drop all
                log.warn("[{}] head reached, dropping with best effort({}/{}), window admitted:{}, {} -> {}", name, accumulatedToDrop, expectedToDrop, admitted, admissionLevel, candidateP);
                admissionLevel = admissionLevel.switchTo(candidateP);
                return;
            }
        }

        // already at head of histogram，while loop not entered
        log.warn("[{}] nothing to drop", name);
    }

    private void admitMore(CountAndTimeWindowState lastWindow) {
        final int currentP = admissionLevel.P();
        if (ADMIT_ALL_P == currentP) {
            return;
        }

        final int admitted = lastWindow.admitted();
        final int expectedAddNextCycle = (int) (policy.getRecoverRate() * admitted);
        int accumulatedAdd = 0;
        // entrySet is in ascending order
        final Iterator<Map.Entry<Integer, AtomicInteger>> ascendingP = lastWindow.histogram().tailMap(currentP, true).entrySet().iterator();
        while (ascendingP.hasNext()) {
            final Map.Entry<Integer, AtomicInteger> entry = ascendingP.next();
            final int candidateP = entry.getKey();
            final int droppedLastCycle = entry.getValue().get();
            accumulatedAdd += droppedLastCycle;
            log.debug("[{}] admit candidate(P:{} dropped:{}), accumulated:{}/{}", name, candidateP, droppedLastCycle, accumulatedAdd, expectedAddNextCycle);

            if (accumulatedAdd >= expectedAddNextCycle) {
                log.warn("[{}] admitting more({}/{}), {} -> {}", name, accumulatedAdd, expectedAddNextCycle, admissionLevel, candidateP);
                admissionLevel = admissionLevel.switchTo(candidateP);
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
