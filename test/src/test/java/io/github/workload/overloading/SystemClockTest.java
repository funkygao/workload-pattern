package io.github.workload.overloading;

import org.junit.jupiter.api.Test;

class SystemClockTest {
    private static SystemClock rtClock = new SystemClock(0);
    private static SystemClock clock = new SystemClock(10); // 10ms精度

    @Test
    void basic() throws InterruptedException {
        for (int i = 0; i < 10; i++) {
            System.out.printf("rt:%d non-rt:%d\n", rtClock.currentTimeMillis(), clock.currentTimeMillis());
            Thread.sleep(2);
        }
    }

}

