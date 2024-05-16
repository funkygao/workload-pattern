package io.github.workload.overloading;

import io.github.workload.BaseConcurrentTest;
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

class OverloadSimulationTest extends BaseConcurrentTest {

    @AfterEach
    void tearDown() {
        AdmissionControllerFactory.resetForTesting();
    }

    @Test
    @EnabledIfSystemProperty(named = "simulate", matches = "true")
    @DisplayName("HTTP准入，请求数量没有大起大落")
    void normal_case_http_only() {
        final FairSafeAdmissionController http = (FairSafeAdmissionController) AdmissionController.getInstance("HTTP");
        final SysloadAdaptiveSimulator sysload = new SysloadAdaptiveSimulator(0.05, 0.002, 400, FairSafeAdmissionController.CPU_USAGE_UPPER_BOUND);
        FairSafeAdmissionController.fairCpu().setSysload(sysload);
        setLogLevel(Level.INFO);

        Runnable businessThread = () -> {
            List<WorkloadPriority> priorities = generateWorkloads(1 << 13, 3);
            Iterator<Integer> steepLatency = new LatencySimulator(20, 600).simulate(priorities.size(), 0.5).iterator();
            for (int i = 0; i < priorities.size(); i++) {
                final WorkloadPriority priority = priorities.get(i);
                sysload.injectRequest();
                final long latencyMs = steepLatency.next();
                final boolean admit = http.admit(Workload.ofPriority(priority));
                //log.info("{} admit:{} latency:{}, {}/{}", priority.simpleString(), admit, latencyMs, i + 1, priorities.size());
                if (admit) {
                    sysload.admit(latencyMs);
                } else {
                    sysload.shed();
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

    private void executeWorkload(boolean admit, long ms) {
        if (admit) {
            sleep(ms);
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
