package io.github.workload.overloading.mock;

import io.github.workload.Sysload;
import io.github.workload.annotations.ThreadSafe;
import io.github.workload.metrics.smoother.ValueSmoother;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

@ThreadSafe
public class SysloadAdaptive implements Sysload {
    private static final Logger log = LoggerFactory.getLogger(SysloadAdaptive.class);
    private static final long MS_IN_SEC = 1000;

    private final double baseCpuUsage;
    private final int maxConcurrency;

    private final AtomicInteger requests = new AtomicInteger(0); // 总请求数
    private final AtomicInteger shed = new AtomicInteger(0); // 累计抛弃请求数
    private final AtomicInteger windowShed = new AtomicInteger(0); // 窗口内抛弃请求数
    private final AtomicLong windowLatency = new AtomicLong(0); // 窗口内latency之和：1秒1个窗口
    private final AtomicInteger randomExhausted = new AtomicInteger(0); // 因为随机而产生线程耗尽的次数
    private final AtomicInteger inflight = new AtomicInteger(0); // 某一时刻的inflight数，qps是1秒内的总数
    private final ConcurrentLinkedQueue<Long> acceptedRequestsTs = new ConcurrentLinkedQueue<>(); // for qps
    private transient double exhaustedFactor;

    private final ReentrantLock cleanupLock = new ReentrantLock();

    /**
     * {@link io.github.workload.overloading.FairShedderCpu#CPU_EMA_ALPHA}
     */
    private final ValueSmoother smoother = ValueSmoother.ofEMA(0.25);

    public SysloadAdaptive() {
        this(0.2, 0, 200);
    }

    public SysloadAdaptive(double baseCpuUsage, double exhaustedFactor, int maxConcurrency) {
        this.baseCpuUsage = baseCpuUsage;
        this.exhaustedFactor = exhaustedFactor;
        this.maxConcurrency = maxConcurrency;
    }

    @Override
    public double cpuUsage() {
        final boolean threadExhausted = threadPoolExhausted();
        final double qps = acceptedRequestsTs.size(); // shed 不会计算在内
        final double avgLatency = qps > 0 ? (double) windowLatency.get() / qps : 0;
        // 考虑到被抛弃的请求在减轻系统负载，我们使用实际处理的请求数量来计算负载因素
        final double loadFactor = (qps / maxConcurrency) + (avgLatency / 1000.0);
        final double dynamicCpuUsage = baseCpuUsage + loadFactor;
        double usage = Math.min(1.0, dynamicCpuUsage); // 确保不会超过100%
        if (usage < 1.0) {
            // 添加随机性以模拟真实环境的波动
            usage = dynamicCpuUsage + ThreadLocalRandom.current().nextDouble(0, 1.0 - usage);
        }
        final double smoothed = smoother.update(usage).smoothedValue();

        log.info("cpu:{}, +rand:{}, smooth:{}, qps:{}, req:{}, shed:{}, latency:{}, inflight:{}, exhausted:{}",
                String.format("%.2f", usage),
                String.format("%.2f", usage - dynamicCpuUsage),
                String.format("%.2f", smoothed),
                qps, requests(), windowShed.get(), String.format("%.0f", avgLatency),
                inflight.get(), threadExhausted);

        windowLatency.set(0);
        windowShed.set(0);
        return smoothed;
    }

    public void injectRequest() {
        requests.incrementAndGet();
        inflight.incrementAndGet();
    }

    public void shed() {
        shed.incrementAndGet();
        inflight.decrementAndGet();
        windowShed.incrementAndGet();
    }

    public void accept(long latencyMs) {
        windowLatency.addAndGet(latencyMs);
        inflight.decrementAndGet();
        acceptedRequestsTs.add(System.currentTimeMillis());
    }

    public int requests() {
        return requests.get();
    }

    public int shedded() {
        return shed.get();
    }

    public boolean threadPoolExhausted() {
        // 使用Little's Law和一定的随机性来判断线程池是否耗尽
        dropExpiredRequests();

        double lambda = acceptedRequestsTs.size();
        double avgLatency = requests() > 0 ? (double) windowLatency.get() / requests() : 0;
        double L = lambda * avgLatency / 1000; // L = λ * W

        boolean exhausted = L > maxConcurrency;
        if (exhausted) {
            log.info("bizPool exhausted, avgLatency:{}, λ:{}, L:{} > {}", avgLatency, lambda, L, maxConcurrency);
        } else {
            // 增加点随机性
            exhausted = ThreadLocalRandom.current().nextDouble() < exhaustedFactor;
            if (exhausted) {
                exhaustedFactor *= (1 - ThreadLocalRandom.current().nextDouble(0.01));
                log.info("bizPool exhausted, random times:{}, requests:{}, factor:{}", randomExhausted.incrementAndGet(), requests(), exhaustedFactor);
            }
        }

        return exhausted;
    }

    private void dropExpiredRequests() {
        cleanupLock.lock();
        try {
            long currentTime = System.currentTimeMillis();
            while (!acceptedRequestsTs.isEmpty() && currentTime - acceptedRequestsTs.peek() > MS_IN_SEC) {
                acceptedRequestsTs.poll();
            }
        } finally {
            cleanupLock.unlock();
        }
    }

}
