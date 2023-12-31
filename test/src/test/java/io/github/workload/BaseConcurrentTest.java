package io.github.workload;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.junit.jupiter.api.AfterAll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

public abstract class BaseConcurrentTest {
    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected final double DELTA = 1e-15; // 用于比较double value的误差

    protected static final int THREAD_COUNT = 26;
    protected static final ExecutorService threadPool = Executors.newFixedThreadPool(THREAD_COUNT);

    @AfterAll
    static void shutdown() {
        Configurator.setLevel("io.github.workload", Level.DEBUG);
    }

    protected void concurrentRun(Runnable runnable) {
        CompletableFuture<?>[] futures = new CompletableFuture[THREAD_COUNT];
        for (int i = 0; i < THREAD_COUNT; i++) {
            futures[i] = CompletableFuture.runAsync(runnable, threadPool);
        }
        CompletableFuture.allOf(futures).join();
    }

    protected <T> List<T> concurrentRun(Supplier<T> task) {
        CompletableFuture<T>[] futures = new CompletableFuture[THREAD_COUNT];
        for (int i = 0; i < THREAD_COUNT; i++) {
            futures[i] = CompletableFuture.supplyAsync(task, threadPool);
        }
        CompletableFuture<Void> allDone = CompletableFuture.allOf(futures);
        allDone.join(); // 等待所有任务完成

        // 收集所有任务的结果
        List<T> results = new ArrayList<>(THREAD_COUNT);
        for (CompletableFuture<T> future : futures) {
            // 假设没有异常，因此使用 .join() 是安全的
            // 如果任务中有异常，这里会抛出 CompletionException
            results.add(future.join());
        }
        return results;
    }

    protected void setLogLevel(Level level) {
        Configurator.setLevel("io.github.workload", level);
    }
}
