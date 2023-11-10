package io.github.workload.overloading;

import org.junit.jupiter.api.Test;

class SystemClockTest {
    private static SystemClock rtClock = new SystemClock(0, null);
    private static SystemClock clock = new SystemClock(10, "xx"); // 10ms精度

    @Test
    void basic() throws InterruptedException {
        long delta = 3;
        for (int i = 0; i < 10; i++) {
            Thread.sleep(delta);
            long t1 = rtClock.currentTimeMillis();
            long t2 = clock.currentTimeMillis();
            System.out.printf("rt:%d non-rt:%d delta:%d\n", t1, t2, t1 - t2);
            //assertTrue(t1 - t2 >= delta);
        }
    }

}

