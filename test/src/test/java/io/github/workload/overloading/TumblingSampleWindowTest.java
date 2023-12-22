package io.github.workload.overloading;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class TumblingSampleWindowTest {
    private static final Logger log = LoggerFactory.getLogger(TumblingSampleWindowTest.class);


    @Test
    void timeUnitConversion() {
        assertEquals(1_000_000, TumblingSampleWindow.NS_PER_MS);
        assertEquals(1_000_000_000, TumblingSampleWindow.DEFAULT_TIME_CYCLE_NS);
    }

    @RepeatedTest(20)
    void basic() throws InterruptedException {
        long nowNs = System.nanoTime();
        TumblingSampleWindow window = new TumblingSampleWindow(nowNs, "");
        for (int i = 0; i < 11; i++) {
            assertFalse(window.sample(WorkloadPriority.of(1, 2), true, System.nanoTime()));
            if (i % 3 == 0) {
                assertFalse(window.sample(WorkloadPriority.of(1, 2), false, System.nanoTime()));
            }
        }
        assertEquals(11, window.admitted());

        window = new TumblingSampleWindow(nowNs, "");
        Thread.sleep(2);
        for (int i = 0; i < 2049; i++) {
            assertFalse(window.sample(RandomUtil.randomWorkloadPriority(), true, System.nanoTime()));
        }
    }

    @Test
    void timeFull() {
        long nowNs = System.nanoTime();
        TumblingSampleWindow window = new TumblingSampleWindow(nowNs, "bar");
        long oneSecondMs = 1000;
        assertFalse(window.sample(WorkloadPriority.ofLowestPriority(), true, nowNs + TumblingSampleWindow.NS_PER_MS * oneSecondMs - 1));
        assertFalse(window.sample(WorkloadPriority.ofLowestPriority(), true, nowNs + TumblingSampleWindow.NS_PER_MS * oneSecondMs - 1));
        assertFalse(window.sample(WorkloadPriority.ofLowestPriority(), true, nowNs + TumblingSampleWindow.NS_PER_MS * oneSecondMs));
        assertTrue(window.sample(WorkloadPriority.ofLowestPriority(), true, nowNs + TumblingSampleWindow.NS_PER_MS * oneSecondMs + 1));
    }

    @Test
    void countFull() {
        TumblingSampleWindow window = new TumblingSampleWindow(System.nanoTime(), "bar");
        for (int i = 0; i < TumblingSampleWindow.DEFAULT_REQUEST_CYCLE; i++) {
            window.sample(RandomUtil.randomWorkloadPriority(), RandomUtil.randomBoolean(), System.nanoTime());
        }
        window.sample(RandomUtil.randomWorkloadPriority(), RandomUtil.randomBoolean(), System.nanoTime());
    }

    @Test
    void histogram() {
        TumblingSampleWindow window = new TumblingSampleWindow(System.nanoTime(), "foo");
        for (int i = 0; i < 100; i++) {
            WorkloadPriority priority = WorkloadPriority.of(i, 0);
            window.sample(priority, true, System.nanoTime());
        }

        ConcurrentSkipListMap<Integer, AtomicInteger> histogram = window.histogram();
        int lastP = -1;
        int n = 0;
        for (Integer p : histogram.tailMap(WorkloadPriority.of(90, 0).P(), false).keySet()) {
            assertTrue(p > lastP);
            lastP = p;
            n++;
            log.info("{}, {}", p, WorkloadPriority.fromP(p).B());
        }
        assertEquals(9, n);
    }

    @Test
    void testToString() {
        TumblingSampleWindow window = new TumblingSampleWindow(System.nanoTime(), "foo");
        assertEquals("Window(request=0,admit=0,counters:0)", window.toString());
        for (int i = 0; i < 100; i++) {
            WorkloadPriority priority = WorkloadPriority.of(i, 0);
            window.sample(priority, true, System.nanoTime());
        }
        assertEquals("Window(request=100,admit=100,counters:100)", window.toString());
        window.sample(WorkloadPriority.of(5, 0), true, System.nanoTime());
        assertEquals("Window(request=101,admit=101,counters:100)", window.toString());
        window.sample(WorkloadPriority.of(5, 0), false, System.nanoTime());
        assertEquals("Window(request=102,admit=101,counters:100)", window.toString());
        window.sample(WorkloadPriority.of(5, 1), false, System.nanoTime());
        assertEquals("Window(request=103,admit=101,counters:101)", window.toString());

        window.restart(System.nanoTime());
        assertEquals("Window(request=0,admit=0,counters:0)", window.toString());
    }

}
