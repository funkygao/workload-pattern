package io.github.workload.overloading;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemClockTest {
    private static SystemClock rtClock = new SystemClock(0, null);
    private static SystemClock clock = new SystemClock(10, "xx"); // 10ms精度

    @Test
    void basic() throws InterruptedException {
        long delta = 2;
        for (int i = 0; i < 10; i++) {
            Thread.sleep(delta); // 2ms
            long t1 = rtClock.currentTimeMillis();
            long t2 = clock.currentTimeMillis();
            System.out.printf("rt:%d non-rt:%d delta:%d\n", t1, t2, t1 - t2);
            assertTrue(t1 - t2 >= delta);
        }
    }

}

