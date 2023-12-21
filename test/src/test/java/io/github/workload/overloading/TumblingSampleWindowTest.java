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
        assertFalse(window.full(System.nanoTime()));
        for (int i = 0; i < 11; i++) {
            window.sample(WorkloadPriority.of(1, 2), true);
            if (i % 3 == 0) {
                window.sample(WorkloadPriority.of(1, 2), false);
            }
        }
        assertFalse(window.full(System.nanoTime())); // 请求量满了
        assertEquals(11, window.admitted());

        window = new TumblingSampleWindow(nowNs, "");
        assertFalse(window.full(System.nanoTime()));
        Thread.sleep(2);
        for (int i = 0; i < 2049; i++) {
            window.sample(RandomUtil.randomWorkloadPriority(), true);
        }
        assertTrue(window.full(System.nanoTime()));
    }

    @Test
    void timeFull() {
        long nowNs = System.nanoTime();
        TumblingSampleWindow window = new TumblingSampleWindow(nowNs, "bar");
        long oneSecondMs = 1000;
        assertFalse(window.full(nowNs + TumblingSampleWindow.NS_PER_MS * oneSecondMs - 1));
        assertFalse(window.full(nowNs + TumblingSampleWindow.NS_PER_MS * oneSecondMs));
        assertTrue(window.full(nowNs + TumblingSampleWindow.NS_PER_MS * oneSecondMs + 1));
    }

    @Test
    void countFull() {
        TumblingSampleWindow window = new TumblingSampleWindow(System.nanoTime(), "bar");
        assertFalse(window.full(System.nanoTime()));
        for (int i = 0; i < TumblingSampleWindow.DEFAULT_REQUEST_CYCLE; i++) {
            window.sample(RandomUtil.randomWorkloadPriority(), RandomUtil.randomBoolean());
        }
        assertFalse(window.full(System.nanoTime()));
        window.sample(RandomUtil.randomWorkloadPriority(), RandomUtil.randomBoolean());
        assertTrue(window.full(System.nanoTime()));
    }

    @Test
    void histogram() {
        TumblingSampleWindow window = new TumblingSampleWindow(System.nanoTime(), "foo");
        for (int i = 0; i < 100; i++) {
            WorkloadPriority priority = WorkloadPriority.of(i, 0);
            window.sample(priority, true);
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
            window.sample(priority, true);
        }
        assertEquals("Window(request=100,admit=100,counters:100)", window.toString());
        window.sample(WorkloadPriority.of(5, 0), true);
        assertEquals("Window(request=101,admit=101,counters:100)", window.toString());
        window.sample(WorkloadPriority.of(5, 0), false);
        assertEquals("Window(request=102,admit=101,counters:100)", window.toString());
        window.sample(WorkloadPriority.of(5, 1), false);
        assertEquals("Window(request=103,admit=101,counters:101)", window.toString());

        window.restart(System.nanoTime());
        assertEquals("Window(request=0,admit=0,counters:0)", window.toString());
    }

}
