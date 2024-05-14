package io.github.workload;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@Disabled // TODO
class SystemClockTest extends BaseConcurrentTest {
    private static final int PRECISION_MS = 15;

    @AfterEach
    void cleanup() {
        SystemClock.resetForTesting();
    }

    @Test
    void singletonIfSamePrecision() {
        String who = "singletonIfSamePrecision";
        List<SystemClock> clocks = concurrentRun(() -> SystemClock.ofPrecisionMs(PRECISION_MS, who));
        SystemClock expectedInstance = SystemClock.ofPrecisionMs(PRECISION_MS, who);
        for (SystemClock clock : clocks) {
            assertSame(expectedInstance, clock);
        }
    }

    @Test
    void illegalArgument() {
        Exception expected = assertThrows(IllegalArgumentException.class, () -> {
            SystemClock.ofPrecisionMs(-1, "");
        });
        assertEquals("precisionMs cannot be negative", expected.getMessage());
    }

    @RepeatedTest(5)
    @Disabled("25ns/op")
    void benchmark_currentTimeMillis() {
        final long N = Integer.MAX_VALUE / 20;
        long t0 = System.nanoTime();
        for (long i = 0; i < N; i++) {
            System.currentTimeMillis();
        }
        log.info("System.currentTimeMillis(): {}ns/op", (System.nanoTime() - t0) / N);
    }

    @RepeatedTest(5)
    @Disabled("30ns/op")
    void benchmark_nanoTime() {
        final long N = Integer.MAX_VALUE / 20;
        long t0 = System.nanoTime();
        for (long i = 0; i < N; i++) {
            System.nanoTime();
        }
        log.info("System.nanoTime(): {}ns/op", (System.nanoTime() - t0) / N);
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
            assertTrue(tRT >= t10);
            assertEquals(0, clock3.currentTimeMillis() - clock15.currentTimeMillis());
        }
    }

    @RepeatedTest(20)
    @Execution(ExecutionMode.CONCURRENT)
    void precision() throws InterruptedException {
        String who = "precision";
        SystemClock rt = SystemClock.ofRealtime(who);
        long precisionMs = 20;
        SystemClock p20 = SystemClock.ofPrecisionMs(precisionMs, who);
        for (int i = 0; i < 20; i++) {
            Thread.sleep(ThreadLocalRandom.current().nextInt(10));
            long err = rt.currentTimeMillis() - p20.currentTimeMillis();
            System.out.println(err);
            assertTrue(err <= precisionMs + SystemClock.PRECISION_DRIFT_MS);
        }
    }

    // 固定周期调度的任务，如果该runnable执行时间长，还未结束就来了下一个周期，那么等该执行完毕还是并发执行？
    // If any execution of this task takes longer than its period, then subsequent executions may start late, but will not concurrently execute.
    @Test
    @Disabled
    void confirmScheduleAtFixedRateHasNoConcurrency() throws InterruptedException {
        // 根据输出结果：每隔1秒输出日志，而不是每10ms
        SystemClock.precisestClockUpdater.scheduleAtFixedRate(() -> {
            try {
                log.info("interval:10ms, will sleep 1s...");
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                // 恢复中断
                Thread.currentThread().interrupt();
            }
        }, 0, 10, TimeUnit.MILLISECONDS);
        Thread.sleep(5000);
        SystemClock.precisestClockUpdater.shutdownNow();
    }
}
