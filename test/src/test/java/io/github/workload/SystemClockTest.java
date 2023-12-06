package io.github.workload;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class SystemClockTest {

    @RepeatedTest(10)
    @Execution(ExecutionMode.CONCURRENT)
    void basic(TestInfo testInfo) {
        SystemClock clock0 = SystemClock.ofPrecisionMs(0);
        SystemClock realtime = SystemClock.ofRealtime();
        assertSame(clock0, realtime);
        assertEquals(clock0, realtime);
        SystemClock clock10 = SystemClock.ofPrecisionMs(10);
        SystemClock clock5 = SystemClock.ofPrecisionMs(5);
        SystemClock clock51 = SystemClock.ofPrecisionMs(5);
        assertSame(clock5, clock51);
        assertNotEquals(clock5, clock10);
        long ta = clock5.currentTimeMillis();
        long tb = clock0.currentTimeMillis();
        assertTrue(tb >= ta);
        assertTrue(clock5.currentTimeMillis() == clock51.currentTimeMillis());
        assertTrue(clock5.currentTimeMillis() == clock10.currentTimeMillis());
    }

    @Test
    void illegalArgument() {
        try {
            SystemClock.ofPrecisionMs(-1);
            fail();
        } catch (IllegalArgumentException expected) {
            assertEquals("precisionMs cannot be negative", expected.getMessage());
        }
    }

    @RepeatedTest(20)
    @Execution(ExecutionMode.CONCURRENT)
    void advanced() throws InterruptedException {
        SystemClock rtClock = SystemClock.ofPrecisionMs(0);
        SystemClock clock10 = SystemClock.ofPrecisionMs(10);
        SystemClock clock3 = SystemClock.ofPrecisionMs(3);
        SystemClock clock15 = SystemClock.ofPrecisionMs(15);
        SystemClock.ofPrecisionMs(10);
        for (int i = 0; i < 10; i++) {
            Thread.sleep(2);
            long tRT = rtClock.currentTimeMillis();
            long t10 = clock10.currentTimeMillis();
            long t3 = clock3.currentTimeMillis();
            assertTrue(tRT >= t10);
            //assertEquals(0, t3 - t10);
            System.out.printf("%d %d\n", clock3.currentTimeMillis() - clock10.currentTimeMillis(),
                    rtClock.currentTimeMillis() - clock3.currentTimeMillis(),
                    rtClock.currentTimeMillis() - clock15.currentTimeMillis());
            //assertEquals(0, clock3.currentTimeMillis() - clock15.currentTimeMillis());
        }
    }

    @RepeatedTest(20)
    @Execution(ExecutionMode.CONCURRENT)
    @Test
    void precision() throws InterruptedException {
        SystemClock rt = SystemClock.ofRealtime();
        long precisionMs = 20;
        SystemClock p20 = SystemClock.ofPrecisionMs(precisionMs);
        Random random = new Random();
        for (int i = 0; i < 20; i++) {
            long err = rt.currentTimeMillis() - p20.currentTimeMillis();
            Thread.sleep(random.nextInt(30));
            assertTrue(err <= precisionMs + SystemClock.PRECISION_DRIFT_MS);
        }
    }

    @Test
    void shutdown() throws InterruptedException {
        SystemClock.ofPrecisionMs(100).currentTimeMillis();
        SystemClock.ofPrecisionMs(20);
        SystemClock.ofPrecisionMs(123);
        Thread.sleep(200);

        for (int i = 0; i < 10; i++) {
            SystemClock.shutdown();
            Thread.sleep(15);
        }
        Thread.sleep(250);
    }

}
