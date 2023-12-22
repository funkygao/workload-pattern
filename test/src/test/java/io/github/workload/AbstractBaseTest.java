package io.github.workload;

import org.junit.jupiter.api.AfterAll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public abstract class AbstractBaseTest {
    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected static final int THREAD_COUNT = 7;
    protected static final ExecutorService threadPool = Executors.newFixedThreadPool(THREAD_COUNT);

    @AfterAll
    static void shutdown() {
        threadPool.shutdownNow();
    }

    protected void concurrentRun(Runnable runnable) {
        CompletableFuture<?>[] futures = new CompletableFuture[THREAD_COUNT];
        for (int i = 0; i < THREAD_COUNT; i++) {
            futures[i] = CompletableFuture.runAsync(runnable, threadPool);
        }
        CompletableFuture.allOf(futures).join();
    }
}
