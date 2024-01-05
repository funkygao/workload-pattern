package io.github.workload.window;

import io.github.workload.BaseConcurrentTest;
import io.github.workload.helper.RandomUtil;
import io.github.workload.overloading.WorkloadPriority;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CountAndTimeWindowStateTest extends BaseConcurrentTest {

    @Test
    void basic() {
        long startNs = System.nanoTime();
        CountAndTimeWindowState state = new CountAndTimeWindowState(startNs);
        assertEquals(startNs, state.getStartNs());
        assertEquals(0, state.requested());
        assertEquals(0, state.admitted());
        assertEquals(0, state.avgQueuedMs());
        assertEquals(0, state.histogram().size());

        // inject 2 workloads
        int P1 = RandomUtil.randomP();
        state.sample(WorkloadPriority.fromP(P1), true);
        int P2 = RandomUtil.randomP();
        while (P1 == P2) {
            P2 = RandomUtil.randomP();
        }
        state.sample(WorkloadPriority.fromP(P2), false);
        assertEquals(1, state.admitted());
        assertEquals(2, state.requested());
        assertEquals(startNs, state.getStartNs());
        assertEquals(2, state.histogram().size());
    }

    @Test
    void tryAcquireRolloverLock() {
        CountAndTimeWindowState state = new CountAndTimeWindowState(System.nanoTime());
        assertTrue(state.tryAcquireRolloverLock());
        assertFalse(state.tryAcquireRolloverLock());
    }

    @Test
    void avgQueuedMs() {
        long startNs = System.nanoTime();
        CountAndTimeWindowState state = new CountAndTimeWindowState(startNs);
        assertEquals(0, state.avgQueuedMs());
        int[] waits = new int[]{6, 6, 2, 8};
        for (int waitNs : waits) {
            state.sample(RandomUtil.randomWorkloadPriority(), true);
            state.waitNs(waitNs * WindowConfig.NS_PER_MS);
        }
        assertEquals(waits.length, state.requested());
        assertEquals(5, state.avgQueuedMs()); // 22/4 => 5.5
    }

    @Test
    void performanceOfCleanup() {
        CountAndTimeWindowState state = new CountAndTimeWindowState(System.nanoTime());
        int N = 10 << 10;
        for (int i = 0; i < N; i++) {
            state.sample(RandomUtil.randomWorkloadPriority(), true);
        }

        CountWindowState state1 = new CountWindowState();
        for (int i = 0; i < N; i++) {
            state1.sample(RandomUtil.randomWorkloadPriority(), true);
        }

        long t0 = System.nanoTime();
        state.cleanup();
        long elapsedNs = System.nanoTime() - t0;

        t0 = System.nanoTime();
        state1.cleanup();
        long elapsedNs1 = System.nanoTime() - t0;

        log.info("{} keys, {} folds, CountAndTimeWindowState:{}ns/{}us, CountWindowState:{}ns/{}us",
                N, elapsedNs / elapsedNs1,
                elapsedNs, elapsedNs / 1000,
                elapsedNs1, elapsedNs1 / 1000);
    }

}