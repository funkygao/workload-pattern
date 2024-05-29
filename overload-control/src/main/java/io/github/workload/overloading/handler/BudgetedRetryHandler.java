package io.github.workload.overloading.handler;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 基于重试预算的过载处理器.
 * <p>
 * <ul>Server overload后，集群上的节点负载分布有两种可能性：
 * <li>均匀，都过载：不能retry，那样会使问题恶化</li>
 * <li>不均，只有(该，少量)节点过载：可以有节制地retry</li>
 * </ul>
 *
 * @see <a href="https://sre.google/sre-book/handling-overload/#handling-overload-errors-AVsjHJ">Google SRE: Handling Overload Errors</a>
 */
@Slf4j
class BudgetedRetryHandler implements OverloadHandler {
    private final long windowTtlMs;

    private final Map<String /* service */, AtomicInteger> totalRequests = new ConcurrentHashMap<>();
    private final Map<String /* service */, AtomicInteger> totalRetries = new ConcurrentHashMap<>();
    private final Map<String /* service */, AtomicLong> lastResetMsMap = new ConcurrentHashMap<>();

    public BudgetedRetryHandler(int windowTtlInSeconds) {
        if (windowTtlInSeconds <= 0) {
            windowTtlInSeconds = 5 * 60; // 5m
        }
        this.windowTtlMs = 1000L * windowTtlInSeconds;
    }

    @Override
    public void recordRequest(String service) {
        counterOf(service, totalRequests).incrementAndGet();
    }

    @Override
    public boolean canRetry(String service, double budget) {
        resetWindowIfNec(service);

        int total = counterOf(service, totalRequests).get();
        if (total == 0) {
            return false;
        }

        AtomicInteger retryCounter = counterOf(service, totalRetries);
        budget = Math.max(0d, Math.min(budget, 1d)); // scale to [0, 1]
        if ((double) retryCounter.get() / total > budget) {
            return false;
        }

        // 消耗一份预算
        retryCounter.incrementAndGet();
        return true;
    }

    private void resetWindowIfNec(String service) {
        long nowMs = System.currentTimeMillis();
        AtomicLong lastResetMs = lastResetMsMap.computeIfAbsent(service, k -> new AtomicLong(nowMs));
        if (nowMs - lastResetMs.get() <= windowTtlMs) {
            // 窗口还没过期
            return;
        }

        synchronized (lastResetMs) {
            // double check to avoid 2 executions
            if (nowMs - lastResetMs.get() > windowTtlMs) {
                AtomicInteger request = counterOf(service, totalRequests);
                AtomicInteger retry = counterOf(service, totalRetries);
                log.info("reset service:{}, (requests:{}, retries:{})", service, request.get(), retry.get());
                request.set(0);
                retry.set(0);
                lastResetMs.set(nowMs);
            }
        }
    }

    private AtomicInteger counterOf(String service, Map<String, AtomicInteger> counters) {
        return counters.computeIfAbsent(service, k -> new AtomicInteger(0));
    }
}
