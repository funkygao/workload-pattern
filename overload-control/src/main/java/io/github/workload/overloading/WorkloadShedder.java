package io.github.workload.overloading;

import io.github.workload.WorkloadPriority;
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
    private static final double ERROR_RATE_BOUND = 1.01d; // 101%

    protected final String name;
    private volatile AdmissionLevel admissionLevel = AdmissionLevel.ofAdmitAll();

    private final TumblingWindow<CountAndTimeWindowState> window;
    private final WorkloadSheddingPolicy policy = new WorkloadSheddingPolicy();

    protected abstract boolean isOverloaded(long nowNs, CountAndTimeWindowState windowState);

    protected WorkloadShedder(String name) {
        this.name = name;
        WindowConfig<CountAndTimeWindowState> config = WindowConfig.create(
                new CountAndTimeRolloverStrategy() {
                    @Override
                    public void onRollover(long nowNs, CountAndTimeWindowState state, TumblingWindow<CountAndTimeWindowState> window) {
                        adaptAdmissionLevel(isOverloaded(nowNs, state), state);
                    }
                }
        );
        this.window = new TumblingWindow(config, name, System.nanoTime());
    }

    boolean admit(@NonNull WorkloadPriority priority) {
        boolean admitted = admissionLevel.admit(priority);
        final long nowNs = System.nanoTime();
        window.advance(priority, admitted, nowNs);
        return admitted;
    }

    protected CountAndTimeWindowState currentWindow() {
        return window.current();
    }

    protected WindowConfig<CountAndTimeWindowState> windowConfig() {
        return window.getConfig();
    }

    private void shedMore(CountAndTimeWindowState lastWindow) {
        final int admitted = lastWindow.admitted();
        final int expectedToDrop = (int) (policy.getDropRate() * admitted);
        if (expectedToDrop == 0) {
            // 上个周期的准入量太少，无法决策抛弃哪个 TODO
            log.info("[{}] unable to shed more: too few window admitted {}", name, admitted);
            return;
        }

        final int currentP = admissionLevel.P();
        final ConcurrentSkipListMap<Integer, AtomicInteger> histogram = lastWindow.histogram();
        int accumulatedToDrop = 0;
        // histogram: (6, 3) -> (9, 10) -> ... -> (112, 2) -> (195, 11) -> (1894, 3)
        // expectedDropNextCycle:4
        // admissionLevel.P=1999/1894, 要切换到112，但195是过度抛弃了，errorRate=(11+3-4)/4=10/4=2.5
        // TODO should we respect currentP?
        final Iterator<Map.Entry<Integer, AtomicInteger>> descendingEntries = histogram.headMap(currentP, true).descendingMap().entrySet().iterator();
        if (!descendingEntries.hasNext()) {
            log.warn("[{}] P:{} beyond histogram, nothing to shed", name, currentP);
            return;
        }

        while (descendingEntries.hasNext()) {
            final Map.Entry<Integer, AtomicInteger> entry = descendingEntries.next();
            final int candidateP = entry.getKey();
            final int candidateRequested = entry.getValue().get();
            accumulatedToDrop += candidateRequested;
            if (log.isDebugEnabled()) {
                log.debug("[{}] shed candidate(P:{} requested:{}), window admitted:{}, accumulated:{}/{}",
                        name, candidateP, candidateRequested, admitted, accumulatedToDrop, expectedToDrop);
            }

            if (accumulatedToDrop >= expectedToDrop) {
                double errorRate = (double) (accumulatedToDrop - expectedToDrop) / expectedToDrop;
                int targetP;
                if (descendingEntries.hasNext() && errorRate < ERROR_RATE_BOUND) {
                    // 误差率可接受，and candidate is not head, candidate will shed workload
                    targetP = descendingEntries.next().getKey();
                    log.warn("[{}] shed more({}/{}), error:{}, window admitted:{}, {} -> {}", name, accumulatedToDrop, expectedToDrop, errorRate, admitted, admissionLevel, targetP);
                } else {
                    targetP = candidateP; // this candidate wil not shed workload
                    // 备选项并没有被shed，提前加上去的退回来
                    accumulatedToDrop -= candidateRequested;
                    // errRate might be negative: below expectation
                    errorRate = (double) (accumulatedToDrop - expectedToDrop) / expectedToDrop;
                    log.warn("[{}] degraded shed more({}/{}), error:{}, window admitted:{}, {} -> {}", name, accumulatedToDrop, expectedToDrop, errorRate, admitted, admissionLevel, targetP);
                }
                admissionLevel = admissionLevel.changeBar(targetP);
                return;
            }

            if (!descendingEntries.hasNext()) {
                // 还不够扣呢，但已经没有可扣的了：we should never shed all
                log.warn("[{}] histogram head reached, degraded shed more({}/{}), window admitted:{}, {} -> {}", name, accumulatedToDrop, expectedToDrop, admitted, admissionLevel, candidateP);
                admissionLevel = admissionLevel.changeBar(candidateP);
                return;
            }
        }
    }

    private void admitMore(CountAndTimeWindowState lastWindow) {
        final int currentP = admissionLevel.P();
        if (ADMIT_ALL_P == currentP) {
            return;
        }

        final int admitted = lastWindow.admitted();
        final int expectedToAdmit = (int) (policy.getRecoverRate() * admitted);
        if (expectedToAdmit == 0) {
            log.info("[{}] unable to admit more: too few window admitted {}", name, admitted);
            return;
        }

        int accumulatedToAdmit = 0;
        final Iterator<Map.Entry<Integer, AtomicInteger>> ascendingP = lastWindow.histogram().tailMap(currentP, false).entrySet().iterator();
        if (!ascendingP.hasNext()) {
            log.warn("[{}] beyond tail of histogram, {} -> {}", name, admissionLevel, AdmissionLevel.ofAdmitAll());
            admissionLevel = AdmissionLevel.ofAdmitAll();
            return;
        }

        while (ascendingP.hasNext()) {
            final Map.Entry<Integer, AtomicInteger> entry = ascendingP.next();
            final int candidateP = entry.getKey();
            final int candidateRequested = entry.getValue().get();
            accumulatedToAdmit += candidateRequested;
            if (log.isDebugEnabled()) {
                log.debug("[{}] admit candidate(P:{} requested:{}), window admitted:{}, accumulated:{}/{}", name, candidateP, candidateRequested, admitted, accumulatedToAdmit, expectedToAdmit);
            }

            if (accumulatedToAdmit >= expectedToAdmit) { // TODO error rate
                log.warn("[{}] admit more({}/{}), window admitted:{}, {} -> {}", name, accumulatedToAdmit, expectedToAdmit, admitted, admissionLevel, candidateP);
                admissionLevel = admissionLevel.changeBar(candidateP);
                return;
            }

            if (!ascendingP.hasNext()) {
                log.warn("[{}] histogram tail reached but still not enough for admit more: happy to admit all", name);
                admissionLevel = AdmissionLevel.ofAdmitAll();
                return;
            }
        }
    }

    @VisibleForTesting
    double dropRate() {
        return policy.getDropRate();
    }

    @VisibleForTesting
    void resetForTesting() {
        this.window.resetForTesting();
        this.admissionLevel = this.admissionLevel.changeBar(WorkloadPriority.MAX_P);
    }

    @VisibleForTesting
    AdmissionLevel admissionLevel() {
        return admissionLevel;
    }

    /**
     * Cross request adaptation.
     *
     * <ul>
     * <li>examine recent behavior</li>
     * <li>take action to improve latency of future requests</li>
     * </ul>
     */
    @VisibleForTesting
    void adaptAdmissionLevel(boolean overloaded, CountAndTimeWindowState lastWindow) {
        if (overloaded) {
            shedMore(lastWindow);
        } else {
            admitMore(lastWindow);
        }
    }

}
