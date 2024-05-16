package io.github.workload.overloading;

import io.github.workload.BaseConcurrentTest;
import io.github.workload.Workload;
import io.github.workload.WorkloadPriority;
import io.github.workload.metrics.tumbling.WindowConfig;
import io.github.workload.overloading.mock.SysloadAdaptive;
import io.github.workload.simulate.LatencySimulator;
import io.github.workload.simulate.WorkloadPrioritySimulator;
import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.Iterator;
import java.util.Map;
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
        final double cpuOverloadThreshold = 0.8;
        System.setProperty(Heuristic.CPU_USAGE_UPPER_BOUND, String.valueOf(cpuOverloadThreshold));
        setLogLevel(Level.INFO);

        final FairSafeAdmissionController http = (FairSafeAdmissionController) AdmissionController.getInstance("HTTP");
        final SysloadAdaptive sysload = new SysloadAdaptive(0.05, 0.002, 400, cpuOverloadThreshold);
        FairSafeAdmissionController.fairCpu().setSysload(sysload);

        final int N = 1 << 10;
        Runnable businessThread = () -> {
            WorkloadPrioritySimulator workloadGenerator = generateRequests(N, 3);
            Iterator<Integer> steepLatency = new LatencySimulator(20, 600).simulate(workloadGenerator.totalRequests(), 0.5).iterator();
            for (Map.Entry<WorkloadPriority, Integer> workload : workloadGenerator) {
                for (int i = 0; i < workload.getValue(); i++) {
                    sysload.injectRequest();

                    long latencyMs = steepLatency.next();
                    if (!http.admit(Workload.ofPriority(workload.getKey()))) {
                        sysload.shed();
                    } else {
                        sysload.accept(latencyMs);
                    }

                    if (sysload.threadPoolExhausted()) {
                        http.feedback(AdmissionController.Feedback.ofOverloaded());
                    } else {
                        http.feedback(AdmissionController.Feedback.ofQueuedNs(latencyMs * WindowConfig.NS_PER_MS));
                    }

                    sleep(latencyMs); // 模拟请求处理时长，以便按照时间轴绘制图
                }
            }
        };

        // 执行并发测试并等待所有线程执行完毕：并发度为 cpu core的2倍
        concurrentRun(businessThread);
        log.info("requested:{}, shed:{}, percent:{}", sysload.requests(), sysload.shedded(), (double) sysload.shedded() * 100d / sysload.requests());
    }

    private WorkloadPrioritySimulator generateRequests(int N, int multiplier) {
        WorkloadPrioritySimulator simulator = new WorkloadPrioritySimulator();
        final int requests = ThreadLocalRandom.current().nextInt(N, N * multiplier);
        simulator.simulateHttpWorkloadPriority(requests);
        log.info("generate:{} -> priorities:{}, requests:{}", N, simulator.size(), simulator.totalRequests());
        return simulator;
    }
}
