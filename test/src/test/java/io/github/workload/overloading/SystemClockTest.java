package io.github.workload.overloading;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SystemClockTest {

    @Test
    @RepeatedTest(10)
    void basic() {
        SystemClock clock0 = SystemClock.getInstance(0);
        SystemClock clock10 = SystemClock.getInstance(10);
        SystemClock clock5 = SystemClock.getInstance(5);
        SystemClock clock51 = SystemClock.getInstance(5);
        assertSame(clock5, clock51);
        assertNotEquals(clock5, clock10);
        long ta = clock5.currentTimeMillis();
        long tb = clock0.currentTimeMillis();
        assertTrue(tb >= ta);
    }

    @Test
    void foo() throws InterruptedException {
        SystemClock rtClock = SystemClock.getInstance(0);
        SystemClock clock = SystemClock.getInstance(10);
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