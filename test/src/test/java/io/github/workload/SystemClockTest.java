package io.github.workload;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class SystemClockTest extends BaseTest {
    private static final int PRECISION_MS = 15;

    @AfterEach
    void cleanup() {
        SystemClock.resetForTesting();
    }

    @Test
    void singletonIfSamePrecision() throws InterruptedException {
        final int numberOfThreads = 100;
        final CyclicBarrier barrier = new CyclicBarrier(numberOfThreads);
        final String who = "singletonIfSamePrecision";
        List<Callable<SystemClock>> tasks = new ArrayList<>();
        for (int i = 0; i < numberOfThreads; i++) {
            tasks.add(() -> {
                barrier.await(); // Ensure all threads start at the same time
                return SystemClock.ofPrecisionMs(PRECISION_MS, who);
            });
        }
        List<SystemClock> clocks = concurrentRun(tasks);
        SystemClock expectedInstance = clocks.get(0);
        for (SystemClock clock : clocks) {
            assertSame(expectedInstance, clock, "All instances should be the same");
        }
    }

    @Test
    void illegalArgument() {
        Exception expected = assertThrows(IllegalArgumentException.class, () -> {
            SystemClock.ofPrecisionMs(-1, "");
        });
        assertEquals("precisionMs cannot be negative", expected.getMessage());
    }

    @RepeatedTest(10)
    @Execution(ExecutionMode.CONCURRENT)
    void basic(TestInfo testInfo) {
        log.info("precisions: 0, 0, 10, 5, 5");
        final String who = "basic";
        SystemClock clock0 = SystemClock.ofPrecisionMs(0, who);
        SystemClock realtime = SystemClock.ofRealtime(who);
        assertSame(clock0, realtime);
        assertEquals(clock0, realtime);
        SystemClock clock10 = SystemClock.ofPrecisionMs(10, who);
        SystemClock clock5 = SystemClock.ofPrecisionMs(5, who);
        SystemClock clock51 = SystemClock.ofPrecisionMs(5, who);
        assertSame(clock5, clock51);
        assertNotEquals(clock5, clock10);
        long ta = clock5.currentTimeMillis();
        long tb = clock0.currentTimeMillis();
        assertTrue(tb >= ta);
        assertTrue(clock5.currentTimeMillis() == clock51.currentTimeMillis());
        log.info("{}", clock5.currentTimeMillis() - clock10.currentTimeMillis());
    }

    @RepeatedTest(20)
    @Execution(ExecutionMode.CONCURRENT)
    void advanced() throws InterruptedException {
        final String who = "advanced";
        SystemClock rtClock = SystemClock.ofPrecisionMs(0, who);
        SystemClock clock10 = SystemClock.ofPrecisionMs(10, who);
        SystemClock clock3 = SystemClock.ofPrecisionMs(3, who);
        SystemClock clock15 = SystemClock.ofPrecisionMs(15, who);
        SystemClock.ofPrecisionMs(10, who);
        for (int i = 0; i < 10; i++) {
            Thread.sleep(2);
            long tRT = rtClock.currentTimeMillis();
            long t10 = clock10.currentTimeMillis();
            // 使用一个小的容忍值来比较两个时钟，以考虑到时钟同步的延迟
            assertTrue(tRT >= t10 - SystemClock.PRECISION_DRIFT_MS);
            long difference = Math.abs(clock3.currentTimeMillis() - clock15.currentTimeMillis());
            assertTrue(difference <= SystemClock.PRECISION_DRIFT_MS);
        }
    }

    @Test
    void precision() {
        String who = "precision";
        SystemClock rt = SystemClock.ofRealtime(who);
        long precisionMs = 20;
        SystemClock p20 = SystemClock.ofPrecisionMs(precisionMs, who);
        boolean passed = false;
        for (int attempt = 0; attempt < 3; attempt++) { // 尝试最多三次
            for (int i = 0; i < 20; i++) {
                long start = System.nanoTime();
                while (TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < precisionMs) {
                    // Busy-wait to avoid Thread.sleep() inaccuracy
                }
                long err = rt.currentTimeMillis() - p20.currentTimeMillis();
                if (err <= precisionMs + SystemClock.PRECISION_DRIFT_MS) {
                    passed = true;
                    break; // 成功，跳出循环
                }
            }
            if (passed) break; // 如果测试通过，不再重试
            // 可能的话，在这里打印一些调试信息
            System.out.println("Retry " + (attempt + 1) + " due to precision error");
        }
        assertTrue(passed, "Precision test passed within retry limit");
    }

    @Test
    void highConcurrency_ofPrecisionMs() throws InterruptedException {
        final int numberOfThreads = 1000;
        final String who = "highConcurrencyTest";
        final CyclicBarrier barrier = new CyclicBarrier(numberOfThreads);
        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (int i = 0; i < numberOfThreads; i++) {
            tasks.add(() -> {
                barrier.await(); // Ensure all threads start at the same time
                SystemClock.ofPrecisionMs(Math.abs(ThreadLocalRandom.current().nextLong()), who);
                return true;
            });
        }
        List<Boolean> results = concurrentRun(tasks);
        for (Boolean result : results) {
            assertTrue(result);
        }
    }

    @Test
    void boundaryConditionTest() {
        final String who = "boundaryConditionTest";
        assertDoesNotThrow(() -> SystemClock.ofPrecisionMs(Long.MAX_VALUE, who));
        assertDoesNotThrow(() -> SystemClock.ofPrecisionMs(0, who));
    }

}
