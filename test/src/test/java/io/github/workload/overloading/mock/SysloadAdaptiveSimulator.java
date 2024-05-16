package io.github.workload.overloading.mock;

import io.github.workload.Sysload;
import io.github.workload.annotations.ThreadSafe;
import io.github.workload.metrics.smoother.ValueSmoother;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

@ThreadSafe
@Slf4j
public class SysloadAdaptiveSimulator implements Sysload {
    private static final Logger log = LoggerFactory.getLogger(SysloadAdaptiveSimulator.class);
    private static final long MS_IN_SEC = 1000;

    private final double baseCpuUsage;
    private final int maxConcurrency;
    private final double cpuOverloadThreshold;

    @Setter
    private String algo = "v2";

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

    public SysloadAdaptiveSimulator() {
        this(0.2, 0, 200, 0.75);
    }

    public SysloadAdaptiveSimulator(double baseCpuUsage, double exhaustedFactor, int maxConcurrency, double cpuOverloadThreshold) {
        this.baseCpuUsage = baseCpuUsage;
        this.exhaustedFactor = exhaustedFactor;
        this.maxConcurrency = maxConcurrency;
        this.cpuOverloadThreshold = cpuOverloadThreshold;
        log.info("baseCpuUsage:{}, exhaustedFactor:{}, maxConcurrency:{}, cpuOverload:{}", baseCpuUsage, exhaustedFactor, maxConcurrency, cpuOverloadThreshold);
    }

    private double cpuUsage_v1() {
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

    private double cpuUsage_v2() {
        // 检查线程池是否耗尽，这可能影响服务能力和CPU使用率的计算
        final boolean threadExhausted = threadPoolExhausted();

        // 计算QPS（每秒请求数），不包括被shed（丢弃）的请求
        final double qps = acceptedRequestsTs.size();

        // 计算平均延迟，如果QPS为0，则延迟为0
        final double avgLatency = qps > 0 ? (double) windowLatency.get() / qps : 0;

        // 计算负载因子，这是一个介于0和1之间的数值，反映了系统负载的程度
        // 负载因子是基于QPS和平均延迟相对于最大并发数的比例
        // 定义权重常量，用于平衡QPS和平均延迟在负载因子计算中的影响
        final double QPS_WEIGHT = 0.5;
        final double LATENCY_WEIGHT = 1 - QPS_WEIGHT;
        final double loadFactor = ((qps / maxConcurrency) * QPS_WEIGHT) + ((avgLatency / 1000.0) * LATENCY_WEIGHT);

        // 计算动态CPU使用率，它基于基础CPU使用率和负载因子
        final double dynamicCpuUsage = baseCpuUsage + loadFactor;

        // 确保CPU使用率不会超过100%
        double usage = Math.min(1.0, dynamicCpuUsage);

        // 根据当前的CPU使用率调整随机增量的大小
        // 当CPU使用率较高时，我们减少随机增量以避免过度波动
        double maxRandomIncrement = 1.0 - usage;
        if (usage > cpuOverloadThreshold) { // 当CPU使用率超过阈值时，减少随机增量的上限
            maxRandomIncrement *= 0.5;
        }

        // 在当前的动态CPU使用率基础上添加随机增量，模拟真实环境中的波动
        usage = dynamicCpuUsage + ThreadLocalRandom.current().nextDouble(0, maxRandomIncrement);

        // 使用平滑器对CPU使用率进行平滑处理，以避免短期内的剧烈波动
        final double smoothed = smoother.update(usage).smoothedValue();

        // 记录日志，包括CPU使用率、增量、平滑值、QPS等关键指标
        // 这有助于调试和监控系统的表现
        log.info("cpu:{}, +rand:{}, smooth:{}, qps:{}, req:{}, shed:{}, latency:{}, inflight:{}, exhausted:{}",
                String.format("%.2f", usage),
                String.format("%.2f", usage - dynamicCpuUsage),
                String.format("%.2f", smoothed),
                qps, requests(), windowShed.get(), String.format("%.0f", avgLatency),
                inflight.get(), threadExhausted);

        // 重置延迟和丢弃请求的计数器，为下一次计算准备
        windowLatency.set(0);
        windowShed.set(0);

        // 返回平滑后的CPU使用率
        return smoothed;
    }

    @Override
    public double cpuUsage() {
        switch (algo) {
            case "v2":
                return cpuUsage_v2();
            default:
                return cpuUsage_v1();
        }
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

    public void admit(long latencyMs) {
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
