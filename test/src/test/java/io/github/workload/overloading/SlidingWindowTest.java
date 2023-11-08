package io.github.workload.overloading;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class SlidingWindowTest {

    @Test
    void timeUnitConversion() {
        assertEquals(1000_000, SlidingWindow.NsPerMs);
        assertEquals(1_000_000_000, SlidingWindow.DefaultTimeCycleNs);
    }

    @Test
    @RepeatedTest(20)
    void basic() throws InterruptedException {
        SlidingWindow window = new SlidingWindow();
        assertFalse(window.full(System.nanoTime()));

        window = new SlidingWindow(SlidingWindow.DefaultTimeCycleNs, 10);
        assertFalse(window.full(System.nanoTime()));
        for (int i = 0; i < 11; i++) {
            window.tick(true);
            if (i % 3 == 0) {
                window.tick(false);
            }
        }
        assertTrue(window.full(System.nanoTime())); // 请求量满了
        assertEquals(11, window.admitted());

        window = new SlidingWindow(TimeUnit.NANOSECONDS.convert(1, TimeUnit.MILLISECONDS), 100);
        assertFalse(window.full(System.nanoTime()));
        Thread.sleep(2);
        assertTrue(window.full(System.nanoTime()));
    }

}