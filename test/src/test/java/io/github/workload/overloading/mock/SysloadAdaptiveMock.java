package io.github.workload.overloading.mock;

import io.github.workload.Sysload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

public class SysloadAdaptiveMock implements Sysload {
    private static final Logger log = LoggerFactory.getLogger(SysloadAdaptiveMock.class);

    /**
     * 基础CPU使用率，即在没有任何请求处理时的系统CPU使用率。
     * 这是CPU使用率计算的起始值，用于模拟系统在空闲状态下的基本负载。
     */
    private final double baseCpuUsage;
    private double exhaustedFactor;
    private final int maxConcurrency;
    private final AtomicInteger requests = new AtomicInteger(0);
    private final AtomicInteger shed = new AtomicInteger(0);
    private final AtomicLong totalLatency = new AtomicLong(0);
    private final AtomicInteger randomExhausted = new AtomicInteger(0);
    private final ConcurrentLinkedQueue<Long> requestTimestamps = new ConcurrentLinkedQueue<>();
    private final long trackingPeriodMs = 1000; // 定义跟踪周期为最近1秒
    private final ReentrantLock cleanupLock = new ReentrantLock();

    public SysloadAdaptiveMock() {
        this(0.2, 0, 200);
    }

    public SysloadAdaptiveMock(double baseCpuUsage, double exhaustedFactor, int maxConcurrency) {
        this.baseCpuUsage = baseCpuUsage;
        this.exhaustedFactor = exhaustedFactor;
        this.maxConcurrency = maxConcurrency;
    }

    @Override
    public double cpuUsage() {
        cleanupExpiredRequests();

        double qps = requestTimestamps.size();
        double avgLatency = requests() > 0 ? (double) totalLatency.get() / requests() : 0;

        double dynamicCpuUsage = Math.min(1.0, baseCpuUsage + (qps + shedded()) / maxConcurrency + avgLatency / 1000.0);
        double usage;
        if (dynamicCpuUsage >= 1.0) {
            usage = 1.0; // CPU 使用率不应超过 100%
        } else {
            usage = dynamicCpuUsage + ThreadLocalRandom.current().nextDouble(0, 1.0 - dynamicCpuUsage);
        }
        log.info("cpu usage:{}/{}, qps:{}/{}, avg latency:{}", usage, dynamicCpuUsage, qps, requests(), avgLatency);
        return usage;
    }

    public void injectRequest(long latencyMs) {
        requestTimestamps.add(System.currentTimeMillis());
        requests.incrementAndGet();
        totalLatency.addAndGet(latencyMs);
    }

    public void shed() {
        shed.incrementAndGet();
    }

    public int requests() {
        return requests.get();
    }

    public int shedded() {
        return shed.get();
    }

    public boolean threadPoolExhausted() {
        // 使用Little's Law和一定的随机性来判断线程池是否耗尽
        cleanupExpiredRequests();

        double lambda = requestTimestamps.size();
        double avgLatency = requests() > 0 ? (double) totalLatency.get() / requests() : 0;
        double L = lambda * avgLatency / 1000; // L = λ * W

        boolean exhausted = L > maxConcurrency;
        if (exhausted) {
            log.info("bizPool exhausted, avgLatency:{}, λ:{}, L:{} > {}", avgLatency, lambda, L, maxConcurrency);
        } else {
            // 增加点随机性
            exhausted = ThreadLocalRandom.current().nextDouble() < exhaustedFactor;
            if (exhausted) {
                exhaustedFactor *= (1 - ThreadLocalRandom.current().nextDouble(0.01));
                log.info("bizPool exhausted, random {}/{}, factor:{}", randomExhausted.incrementAndGet(), requests(), exhaustedFactor);
            }
        }

        return exhausted;
    }

    private void cleanupExpiredRequests() {
        cleanupLock.lock();
        try {
            long currentTime = System.currentTimeMillis();
            while (!requestTimestamps.isEmpty() && currentTime - requestTimestamps.peek() > trackingPeriodMs) {
                requestTimestamps.poll();
            }
        } finally {
            cleanupLock.unlock();
        }
    }
}
