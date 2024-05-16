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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

class OverloadSimulationTest extends BaseTest {
    private final int maxConcurrency = 400;
    private final double exhaustedFactor = 0.0002;
    private final int N = 1 << 13;
    private final int latencyLow = 20;
    private final int latencyHigh = 400;
    private final double latencySteepness = 0.5;
    private final int latencyFactor = maxConcurrency / (2 * THREAD_COUNT); // 为了模拟多客户端并发，否则无法提升qps

    @AfterEach
    void tearDown() {
        AdmissionControllerFactory.resetForTesting();
    }

    @EnabledIfSystemProperty(named = "simulate", matches = "true")
    @Test
    void normal_case_http_only() {
        final FairSafeAdmissionController http = (FairSafeAdmissionController) AdmissionController.getInstance("HTTP");
        final SysloadAdaptiveSimulator sysload = new SysloadAdaptiveSimulator(0.05, exhaustedFactor, maxConcurrency, FairSafeAdmissionController.CPU_USAGE_UPPER_BOUND).withAlgo("v2");
        FairSafeAdmissionController.fairCpu().setSysload(sysload);
        setLogLevel(Level.INFO);

        Runnable businessThread = () -> {
            List<WorkloadPriority> priorities = generateWorkloads(N, 3);
            Iterator<Integer> steepLatency = new LatencySimulator(latencyLow, latencyHigh).simulate(priorities.size(), latencySteepness).iterator();
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

                executeWorkload(admit, latencyMs);
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
        // FIXME 生成订单优先级都一样的数量
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
}
