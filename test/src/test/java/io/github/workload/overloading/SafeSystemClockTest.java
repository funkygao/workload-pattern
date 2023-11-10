package io.github.workload.overloading;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SafeSystemClockTest {

    @Test
    @RepeatedTest(10)
    void basic() {
        SafeSystemClock clock0 = SafeSystemClock.getInstance(0);
        SafeSystemClock clock10 = SafeSystemClock.getInstance(10);
        SafeSystemClock clock5 = SafeSystemClock.getInstance(5);
        SafeSystemClock clock51 = SafeSystemClock.getInstance(5);
        assertSame(clock5, clock51);
        assertNotEquals(clock5, clock10);
        long ta = clock5.currentTimeMillis();
        long tb = clock0.currentTimeMillis();
        assertTrue(tb >= ta);
    }

}