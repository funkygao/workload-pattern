package io.github.workload.overloading;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.github.workload.BaseConcurrentTest;
import io.github.workload.Workload;
import io.github.workload.WorkloadPriority;
import io.github.workload.helper.LogUtil;
import io.github.workload.metrics.tumbling.TumblingWindow;
import io.github.workload.metrics.tumbling.WindowConfig;
import io.github.workload.overloading.mock.SysloadAdaptiveMock;
import io.github.workload.simulate.WorkloadPrioritySimulator;
import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
        SysloadAdaptiveMock sysload = new SysloadAdaptiveMock(0.05, 0.008, 200);
        FairSafeAdmissionController.fairCpu().setSysload(sysload);

        final int latencyLow = 10;
        final int latencyHigh = 300;
        final int baseN = 1 << 10;
        final Random random = new Random();

        Runnable businessThread = () -> {
            WorkloadPrioritySimulator generator = generateWorkloadPriorities((int) (1 + random.nextDouble()) * baseN);
            for (Map.Entry<WorkloadPriority, Integer> entry : generator) {
                for (int i = 0; i < entry.getValue(); i++) {
                    // 请求
                    final WorkloadPriority priority = entry.getKey();
                    long latencyMs = random.nextInt(latencyHigh - latencyLow) + latencyLow;
                    log.debug("http request: {}, latencyMs: {}", priority.simpleString(), latencyMs);
                    sysload.injectRequest(latencyMs);

                    // 准入
                    if (!http.admit(Workload.ofPriority(priority))) {
                        sysload.shed();
                        log.info("http shed: {}, total requests:{}, shedded:{}", priority.simpleString(), sysload.requests(), sysload.shedded());
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

    private WorkloadPrioritySimulator generateWorkloadPriorities(int N) {
        WorkloadPrioritySimulator simulator = new WorkloadPrioritySimulator();
        simulator.simulateHttpWorkloadPriority(N);
        long sleepMs = 300;
        log.info("generate:{} -> priorities:{}, requests:{}, will sleep:{}s", N, simulator.size(), simulator.totalRequests(), sleepMs);
        sleep(sleepMs);
        return simulator;
    }

    @Test
    @Disabled
    void test_logging() {
        AdmissionControllerFactory.resetForTesting();

        ListAppender<ILoggingEvent> l_acf = LogUtil.setupAppender(AdmissionControllerFactory.class);
        ListAppender<ILoggingEvent> l_container = LogUtil.setupAppender(ContainerLoad.class);
        ListAppender<ILoggingEvent> l_window = LogUtil.setupAppender(TumblingWindow.class);
        ListAppender<ILoggingEvent> l_cpu_shed = LogUtil.setupAppender(FairShedderCpu.class);
        ListAppender<ILoggingEvent> l_queue = LogUtil.setupAppender(FairShedderQueue.class);

        AdmissionController http = AdmissionController.getInstance("HTTP");
        AdmissionController rpc = AdmissionController.getInstance("RPC");
        rpc.admit(Workload.ofPriority(WorkloadPriority.fromP(553)));
        http.feedback(AdmissionController.Feedback.ofOverloaded());

        assertEquals(2, l_acf.list.size());
        assertEquals("register for:HTTP", l_acf.list.get(0).getFormattedMessage());
        assertEquals("register for:RPC", l_acf.list.get(1).getFormattedMessage());

        assertEquals(1, l_container.list.size());
        assertEquals("created with coolOff:600 sec", l_container.list.get(0).getFormattedMessage());

        assertEquals(3, l_window.list.size());
        assertEquals("[CPU] created with WindowConfig(time=1s,count=2048)", l_window.list.get(0).getFormattedMessage());
        assertEquals("[HTTP] created with WindowConfig(time=1s,count=2048)", l_window.list.get(1).getFormattedMessage());
        assertEquals("[RPC] created with WindowConfig(time=1s,count=2048)", l_window.list.get(2).getFormattedMessage());

        assertEquals(1, l_cpu_shed.list.size());
        assertEquals("[CPU] created with sysload:ContainerLoad, upper bound:0.75, ema alpha:0.25", l_cpu_shed.list.get(0).getFormattedMessage());

        assertEquals(1, l_queue.list.size());
        assertEquals("[HTTP] got explicit overload feedback", l_queue.list.get(0).getFormattedMessage());
    }

}
