package io.github.workload.overloading;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SamplingWindowTest {

    @Test
    void timeUnitConversion() {
        assertEquals(1_000_000, SamplingWindow.NS_PER_MS);
        assertEquals(1_000_000_000, SamplingWindow.DEFAULT_TIME_CYCLE_NS);
    }

    @RepeatedTest(20)
    void basic() throws InterruptedException {
        long nowNs = System.nanoTime();
        SamplingWindow window = new SamplingWindow(nowNs);
        assertFalse(window.full(System.nanoTime()));
        for (int i = 0; i < 11; i++) {
            window.sample(WorkloadPriority.of(1, 2), true);
            if (i % 3 == 0) {
                window.sample(WorkloadPriority.of(1, 2), false);
            }
        }
        assertFalse(window.full(System.nanoTime())); // 请求量满了
        assertEquals(11, window.admitted());

        window = new SamplingWindow(nowNs);
        assertFalse(window.full(System.nanoTime()));
        Thread.sleep(2);
        for (int i = 0; i < 2049; i++) {
            window.sample(TestingUtil.randomWorkloadPriority(), true);
        }
        assertTrue(window.full(System.nanoTime()));
    }

}
