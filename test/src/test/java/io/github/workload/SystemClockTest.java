package io.github.workload;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class SystemClockTest {
    private static final Logger log = LoggerFactory.getLogger(SystemClockTest.class);

    private static final int THREAD_COUNT = 10;
    private static final int PRECISION_MS = 15;
    private static final ExecutorService executorService = Executors.newFixedThreadPool(THREAD_COUNT);

    @AfterAll
    static void cleanup() {
        executorService.shutdownNow();
        SystemClock.shutdown();
    }

    // 固定周期调度的任务，如果该runnable执行时间长，还未结束就来了下一个周期，那么等该执行完毕还是并发执行？
    // If any execution of this task takes longer than its period, then subsequent executions may start late, but will not concurrently execute.
    @Test
    void confirmScheduleAtFixedRateHasNoConcurrency() throws InterruptedException {
        // 根据输出结果：每隔1秒输出日志，而不是每10ms
        SystemClock.precisestClockUpdater.scheduleAtFixedRate(() -> {
            try {
                log.info("ha");
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                // 恢复中断
                Thread.currentThread().interrupt();
            }
        }, 0, 10, TimeUnit.MILLISECONDS);
        Thread.sleep(5000);
        SystemClock.precisestClockUpdater.shutdownNow();
    }

    @Test
    void testSingletonProperty() throws InterruptedException, ExecutionException {
        Callable<SystemClock> task = () -> SystemClock.ofPrecisionMs(PRECISION_MS);
        List<Future<SystemClock>> futures = new ArrayList<>();
        for (int i = 0; i < THREAD_COUNT; i++) {
            futures.add(executorService.submit(task));
        }

        SystemClock expectedInstance = SystemClock.ofPrecisionMs(PRECISION_MS);
        for (Future<SystemClock> future : futures) {
            SystemClock clockInstance = future.get();
            assertSame(expectedInstance, clockInstance);
        }
    }

    @Test
    void testConcurrentTimeMillisAccuracy() {
        List<Long> timestamps = Collections.synchronizedList(new ArrayList<>());

        Runnable task = () -> {
            SystemClock clock = SystemClock.ofPrecisionMs(PRECISION_MS);
            for (int i = 0; i < 100; i++) {
                timestamps.add(clock.currentTimeMillis());
            }
        };

        CompletableFuture<?>[] futures = new CompletableFuture[THREAD_COUNT];
        for (int i = 0; i < THREAD_COUNT; i++) {
            futures[i] = CompletableFuture.runAsync(task, executorService);
        }
        CompletableFuture.allOf(futures).join();

        long previous = Long.MIN_VALUE;
        for (long timestamp : timestamps) {
            assertTrue(timestamp >= previous, "Timestamps are not monotonically increasing");
            previous = timestamp;
        }
    }

    @Test
    void testShutdown() {
        SystemClock clock = SystemClock.ofPrecisionMs(PRECISION_MS);
        assertNotNull(clock);

        SystemClock.shutdown(); // should terminate the timer task
        assertTrue(SystemClock.precisestClockUpdater.isShutdown(), "Scheduled executor service should be shutdown");
    }

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
        System.out.println(clock5.currentTimeMillis() - clock10.currentTimeMillis());
        //assertTrue(clock5.currentTimeMillis() == clock10.currentTimeMillis());
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
            if (false) {
                System.out.printf("%d %d\n", clock3.currentTimeMillis() - clock10.currentTimeMillis(),
                        rtClock.currentTimeMillis() - clock3.currentTimeMillis(),
                        rtClock.currentTimeMillis() - clock15.currentTimeMillis());
            }

            assertEquals(0, clock3.currentTimeMillis() - clock15.currentTimeMillis());
        }
    }

    @RepeatedTest(20)
    @Execution(ExecutionMode.CONCURRENT)
    void precision() throws InterruptedException {
        SystemClock rt = SystemClock.ofRealtime();
        long precisionMs = 20;
        SystemClock p20 = SystemClock.ofPrecisionMs(precisionMs);
        for (int i = 0; i < 20; i++) {
            long err = rt.currentTimeMillis() - p20.currentTimeMillis();
            Thread.sleep(ThreadLocalRandom.current().nextInt(30));
            assertTrue(err <= precisionMs + SystemClock.PRECISION_DRIFT_MS * 2);
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
