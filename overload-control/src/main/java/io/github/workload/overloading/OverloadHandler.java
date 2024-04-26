package io.github.workload.overloading;

import io.github.workload.annotations.ThreadSafe;
import lombok.extern.slf4j.Slf4j;

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
 * @see <a href="https://sre.google/sre-book/handling-overload/#handling-overload-errors-AVsjHJ">Google SRE: Handling Overload Errors</a>
 */
@Slf4j
@ThreadSafe
public class OverloadHandler {

    // 计数器，用于判断是否整个数据中心都过载
    private final Map<String, AtomicInteger> totalRequests = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> totalRetries = new ConcurrentHashMap<>();

    // 计数器定期reset
    private final long resetIntervalMs;
    private volatile long lastResetMillis = System.currentTimeMillis();
    private final Map<String, ReentrantLock> resetLocks = new ConcurrentHashMap<>();

    public OverloadHandler(int resetIntervalSecond) {
        if (resetIntervalSecond <= 0) {
            resetIntervalSecond = 5 * 60; // 5m
        }
        this.resetIntervalMs = 1000L * resetIntervalSecond;
    }

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
     * <p>下游是大范围还只是小范围过载？只有小范围才可以重试</p>
     * <ul>重试预算设置取决于多个因素：
     * <li>服务的重要性：对于关键服务，可能希望设置较高的重试预算，以确保在可能的情况下尽量完成请求</li>
     * <li>失败的成本：如果一个请求失败的成本很高，例如，它可能导致数据丢失或用户体验显著下降，那么可能需要一个较高的重试预算</li>
     * <li>重试带来的额外负载：重试会增加系统的总体负载，特别是在高并发的情况下。需要评估系统是否能够处理这种额外的负载</li>
     * <li>依赖服务的限制：如果你的服务依赖于其他服务，那么你的重试策略可能需要考虑这些依赖服务的限制和它们的重试策略</li>
     * </ul>
     *
     * @param service     服务
     * @param retryBudget 重试预算，(0.0, 1.0)，通常设为0.1，即10%
     * @return true if you can retry
     */
    public boolean attemptRetry(String service, double retryBudget) {
        resetIfNec(service);

        int total = counterOf(service, totalRequests).get();
        if (total == 0) {
            return false;
        }

        AtomicInteger retryCounter = counterOf(service, totalRetries);
        if ((double) retryCounter.get() / total > retryBudget) {
            return false;
        }

        // 消耗一份预算
        retryCounter.incrementAndGet();
        return true;
    }

    private void resetIfNec(String service) {
        long nowMs = System.currentTimeMillis();
        ReentrantLock resetLock = resetLockOf(service);
        if (nowMs - lastResetMillis > resetIntervalMs) {
            // 并发时多个线程进来
            if (resetLock.tryLock()) {
                try {
                    if (nowMs - lastResetMillis > resetIntervalMs) {
                        // 如果没有这个double check，winner释放锁后，其他线程会重复reset
                        AtomicInteger request = counterOf(service, totalRequests);
                        AtomicInteger retry = counterOf(service, totalRetries);
                        log.info("reset service:{}, (request:{}, retry:{})", service, request.get(), retry.get());
                        request.set(0);
                        retry.set(0);

                        lastResetMillis = nowMs; // so that double check returns false
                    }
                } finally {
                    resetLock.unlock();
                }
            }
        }
    }

    private AtomicInteger counterOf(String service, Map<String, AtomicInteger> counters) {
        return counters.computeIfAbsent(service, k -> new AtomicInteger(0));
    }

    private ReentrantLock resetLockOf(String service) {
        return resetLocks.computeIfAbsent(service, k -> new ReentrantLock());
    }
}
