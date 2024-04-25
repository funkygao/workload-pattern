package io.github.workload.overloading;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Client在遇到Server overload响应后该如何处理.
 * <p>
 * <ul>Server overload后，集群上的节点负载分布有两种可能性：
 * <li>均匀，都过载：不能retry，那样会使问题恶化</li>
 * <li>不均，只有(该，少量)节点过载：可以有节制地retry</li>
 * </ul>
 *
 * @see https://hormozk.com/capacity
 */
public class OverloadHandler {
    private static final long RESET_INTERVAL_MS = 1000 * 60 * 5;

    private volatile long lastResetMillis = System.currentTimeMillis();

    private final Map<String, AtomicInteger> totalRequests = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> retryRequests = new ConcurrentHashMap<>();
    private final Map<String, ReentrantLock> resetLocks = new ConcurrentHashMap<>();

    /**
     * 记录下来：先该服务发送了一次请求.
     *
     * @param service 服务
     */
    public void sendRequest(String service) {
        counterOf(service, totalRequests).incrementAndGet();
    }

    /**
     * 在指定预算下是否可以发起重试，以便可能分发到低负载节点.
     *
     * @param service     服务
     * @param retryBudget (0.0, 1.0)，通常设为0.1，即10%
     * @return true if you can retry
     */
    public boolean attemptRetry(String service, double retryBudget) {
        resetIfNec(service);

        int total = counterOf(service, totalRequests).get();
        if (total == 0) {
            return false;
        }

        AtomicInteger retryCounter = counterOf(service, retryRequests);
        double retry = (double) retryCounter.get();
        if (retry / total > retryBudget) {
            return false;
        }

        // 消耗一份预算
        retryCounter.incrementAndGet();
        return true;
    }

    private void resetIfNec(String service) {
        long nowMs = System.currentTimeMillis();
        ReentrantLock resetLock = resetLockOf(service);
        if (nowMs - lastResetMillis > RESET_INTERVAL_MS) {
            if (resetLock.tryLock()) {
                // double check
                counterOf(service, totalRequests).set(0);
                counterOf(service, retryRequests).set(0);
                lastResetMillis = nowMs;
                resetLock.unlock();
            }
        }
    }

    private AtomicInteger counterOf(String service, Map<String, AtomicInteger> counters) {
        return counters.putIfAbsent(service, new AtomicInteger(0));
    }

    private ReentrantLock resetLockOf(String service) {
        return resetLocks.putIfAbsent(service, new ReentrantLock());
    }
}
