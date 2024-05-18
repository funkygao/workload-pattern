package io.github.workload.overloading;

import io.github.workload.BaseTest;
import io.github.workload.Workload;
import io.github.workload.WorkloadPriority;
import io.github.workload.metrics.tumbling.WindowConfig;
import io.github.workload.overloading.mock.SysloadAdaptiveSimulator;
import io.github.workload.simulate.LatencySimulator;
import io.github.workload.simulate.WorkloadPrioritySimulator;
import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

class OverloadSimulationTest extends BaseTest {
    private final double latencySteepness = 0.5;

    @AfterEach
    void tearDown() {
        AdmissionControllerFactory.resetForTesting();
    }

    @EnabledIfSystemProperty(named = "simulate", matches = "true")
    @DisplayName("压力维持在CPU阈值附近")
    @Test
    void case_continuous_busy() {
        setLogLevel(Level.INFO);

        Config c = new Config();
        c.N = 4 << 10;
        c.maxConcurrency = 400;
        c.exhaustedFactor = 0.0002;
        c.latencyLow = 20;
        c.latencyHigh = 400;
        c.latencySteepness = 0.5;
        simulate(c);
    }

    @EnabledIfSystemProperty(named = "simulate", matches = "true")
    @DisplayName("请求少，但导致CPU飙升，而滚动窗口可shed数量少的降级场景")
    @Test
    void case_idle_greedy() {
        setLogLevel(Level.TRACE);

        Config c = new Config();
        c.exhaustedFactor = 0.1;
        c.N = 1 << 10;
        c.maxConcurrency = 400;
        c.laziness = 0.21; // 越大sleep越久
        c.latencyLow = 20;
        c.latencyHigh = 3000;
        c.latencySteepness = 0.5;
        simulate(c);
    }

    @EnabledIfSystemProperty(named = "simulate", matches = "true")
    @DisplayName("脉冲式请求压力")
    @Test
    void case_lazy_jitter() {
        setLogLevel(Level.DEBUG);

        Config c = new Config();
        c.N = 1 << 10;
        c.maxConcurrency = 400;
        c.laziness = 0.76; // 越大sleep越久
        c.exhaustedFactor = 0.0002;
        c.latencyLow = 20;
        c.latencyHigh = 30;
        c.latencySteepness = 0.5;
        c.latencySleepFactor = 400 / (2 * THREAD_COUNT); // 为了模拟多客户端并发，否则无法提升qps
        simulate(c);
    }

    private void simulate(Config c) {
        final FairSafeAdmissionController http = (FairSafeAdmissionController) AdmissionController.getInstance("HTTP");
        final SysloadAdaptiveSimulator sysload = new SysloadAdaptiveSimulator(0.05, c.exhaustedFactor, c.maxConcurrency, FairShedderCpu.CPU_USAGE_UPPER_BOUND).withAlgo("v2");
        FairSafeAdmissionController.fairCpu().setSysload(sysload);

        Runnable businessThread = () -> {
            List<WorkloadPriority> priorities = generateWorkloads(c.N, 3);
            Iterator<Integer> steepLatency = new LatencySimulator(c.latencyLow, c.latencyHigh).simulate(priorities.size(), latencySteepness).iterator();
            for (WorkloadPriority priority : priorities) {
                sysload.injectRequest();
                final boolean admit = http.admit(Workload.ofPriority(priority));
                final long latencyMs = steepLatency.next();
                if (admit) {
                    sysload.admit(latencyMs);
                } else {
                    sysload.shed(latencyMs);
                }

                if (sysload.threadPoolExhausted()) {
                    http.feedback(AdmissionController.Feedback.ofOverloaded());
                } else {
                    http.feedback(AdmissionController.Feedback.ofQueuedNs(latencyMs * WindowConfig.NS_PER_MS / 10));
                }

                executeWorkload(admit, latencyMs / c.latencySleepFactor);
                long delay = sysload.pulseDelay(c.laziness, 5000);
                if (delay > 0) {
                    sleep(delay);
                }
            }
        };

        concurrentRun(businessThread);
        log.info("requested:{}, shed:{}, percent:{}", sysload.requests(), sysload.shedded(), (double) sysload.shedded() * 100d / sysload.requests());
    }

    private void executeWorkload(boolean admit, long cost) {
        if (admit) {
            sleep(cost);
        }
    }

    private List<WorkloadPriority> generateWorkloads(int N, int multiplier) {
        N = ThreadLocalRandom.current().nextInt(N, N * multiplier);
        WorkloadPrioritySimulator simulator = new WorkloadPrioritySimulator().simulateHttpWorkloadPriority(N);
        log.info("generate:{}, priorities:{}", N, simulator.size());

        List<WorkloadPriority> priorities = new ArrayList<>(simulator.totalRequests());
        for (Map.Entry<WorkloadPriority, Integer> workload : simulator) {
            for (int i = 0; i < workload.getValue(); i++) {
                priorities.add(workload.getKey());
            }
        }
        Collections.shuffle(priorities); // 模拟请求结构均匀分布
        return priorities;
    }

    @Test
    void pulseDelay() {
        SysloadAdaptiveSimulator simulator = new SysloadAdaptiveSimulator();
        final int n = 10;
        List<Long> l = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            l.add(simulator.pulseDelay(0.1, 1000));
        }
        log.info("0.1, {}", l);

        l.clear();
        for (int i = 0; i < n; i++) {
            l.add(simulator.pulseDelay(0.4, 1000));
        }
        log.info("0.4, {}", l);

        l.clear();
        for (int i = 0; i < n; i++) {
            l.add(simulator.pulseDelay(0.8, 1000));
        }
        log.info("0.8, {}", l);
    }

    static class Config {
        int N;
        int maxConcurrency;
        double exhaustedFactor;
        int latencyLow;
        int latencyHigh;
        double latencySteepness;
        int latencySleepFactor = 1;
        double laziness = 0;
    }
}
