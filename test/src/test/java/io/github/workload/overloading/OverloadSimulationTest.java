package io.github.workload.overloading;

import io.github.workload.BaseConcurrentTest;
import io.github.workload.Workload;
import io.github.workload.WorkloadPriority;
import io.github.workload.metrics.tumbling.WindowConfig;
import io.github.workload.overloading.mock.SysloadAdaptive;
import io.github.workload.simulate.WorkloadPrioritySimulator;
import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

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
        long t0 = System.nanoTime();
        System.setProperty(Heuristic.CPU_USAGE_UPPER_BOUND, "0.69");
        setLogLevel(Level.INFO);

        FairSafeAdmissionController http = (FairSafeAdmissionController) AdmissionController.getInstance("HTTP");
        SysloadAdaptive sysload = new SysloadAdaptive(0.05, 0.002, 200);
        FairSafeAdmissionController.fairCpu().setSysload(sysload);

        final int latencyLow = 10;
        final int latencyHigh = 300;
        final int N = 1 << 10;

        // 构造业务线程，处理请求
        Runnable businessThread = () -> {
            WorkloadPrioritySimulator generator = generateRequests(N, 3);
            for (Map.Entry<WorkloadPriority, Integer> entry : generator) {
                for (int i = 0; i < entry.getValue(); i++) {
                    // 请求
                    final WorkloadPriority priority = entry.getKey();
                    long latencyMs = ThreadLocalRandom.current().nextInt(latencyHigh - latencyLow) + latencyLow;
                    log.debug("http request: {}, latencyMs: {}", priority.simpleString(), latencyMs);
                    sysload.injectRequest();

                    // 准入
                    if (!http.admit(Workload.ofPriority(priority))) {
                        sysload.shed();
                        log.info("http shed: {}, total requests:{}, shedded:{}", priority.simpleString(), sysload.requests(), sysload.shedded());
                    } else {
                        sysload.accept(latencyMs);
                    }

                    // 反馈
                    if (sysload.threadPoolExhausted()) {
                        http.feedback(AdmissionController.Feedback.ofOverloaded());
                    } else {
                        http.feedback(AdmissionController.Feedback.ofQueuedNs(latencyMs * WindowConfig.NS_PER_MS));
                    }

                    // 模拟请求处理时长
                    sleep(latencyMs);
                }
            }
        };

        // 执行并发测试并等待所有线程执行完毕：并发度为 cpu core的2倍
        concurrentRun(businessThread);
        log.info("elapsed: {}s", (System.nanoTime() - t0) / (WindowConfig.NS_PER_MS * 1000));
    }

    private WorkloadPrioritySimulator generateRequests(int N, int multiplier) {
        WorkloadPrioritySimulator simulator = new WorkloadPrioritySimulator();
        final int requests = ThreadLocalRandom.current().nextInt(N, N * multiplier);
        simulator.simulateHttpWorkloadPriority(requests);
        log.info("generate:{} -> priorities:{}, requests:{}", N, simulator.size(), simulator.totalRequests());
        return simulator;
    }
}
