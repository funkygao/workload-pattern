package io.github.workload.overloading;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.github.workload.BaseConcurrentTest;
import io.github.workload.Workload;
import io.github.workload.WorkloadPriority;
import io.github.workload.helper.LogUtil;
import io.github.workload.metrics.tumbling.TumblingWindow;
import io.github.workload.overloading.mock.SysloadMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IntegrationTest extends BaseConcurrentTest {

    @AfterEach
    void tearDown() {
        AdmissionControllerFactory.resetForTesting();
    }

    @Test
    void simulate() {
        System.setProperty(Heuristic.CPU_USAGE_UPPER_BOUND, "0.69");

        FairSafeAdmissionController http = (FairSafeAdmissionController) AdmissionController.getInstance("HTTP");
        FairSafeAdmissionController rpc = (FairSafeAdmissionController) AdmissionController.getInstance("RPC");
        FairSafeAdmissionController.shedderOnCpu().sysload = new SysloadMock(0.3);
        ContainerLoad.stop();
        final int[] B = new int[]{2, 5, 10, 20, 40};
        final int latencyMsBaseline = 10;
        final int maxUid = (1 << 7) - 1;
        final int threadPoolExhaustedPercentage = 2; // 2%
        final int N = 1 << 10;
        Runnable task = () -> {
            for (int i = 0; i < N; i++) {
                long t0 = System.nanoTime();
                int Bi = ThreadLocalRandom.current().nextInt(B.length);
                int Uid = ThreadLocalRandom.current().nextInt(maxUid);
                WorkloadPriority priority = WorkloadPriority.ofPeriodicRandomFromUID(B[Bi], Uid);
                long latencyMs = ThreadLocalRandom.current().nextInt(latencyMsBaseline);
                final Workload workload = Workload.ofPriority(priority);
                if (!http.admit(workload)) {
                    log.info("http shed: {}", workload.getPriority());
                }
                sleep(latencyMs);
                if (ThreadLocalRandom.current().nextInt(100) < threadPoolExhaustedPercentage) {
                    http.feedback(AdmissionController.Feedback.ofOverloaded());
                } else {
                    http.feedback(AdmissionController.Feedback.ofQueuedNs(System.nanoTime() - t0));
                }

                if (ThreadLocalRandom.current().nextBoolean()) {
                    // 1 rpc every 2 http
                    if (!rpc.admit(workload)) {
                        log.info(" rpc shed: {}", workload.getPriority());
                    }
                    rpc.feedback(AdmissionController.Feedback.ofQueuedNs(System.nanoTime() - t0));
                }
            }
            log.info("{} workload emit", N);
        };
        concurrentRun(task);
    }

    @Test
    @Disabled
    void test_logging() {
        AdmissionControllerFactory.resetForTesting();

        ListAppender<ILoggingEvent> l_acf = LogUtil.setupAppender(AdmissionControllerFactory.class);
        ListAppender<ILoggingEvent> l_container = LogUtil.setupAppender(ContainerLoad.class);
        ListAppender<ILoggingEvent> l_window = LogUtil.setupAppender(TumblingWindow.class);
        ListAppender<ILoggingEvent> l_cpu_shed = LogUtil.setupAppender(WorkloadShedderOnCpu.class);
        ListAppender<ILoggingEvent> l_queue = LogUtil.setupAppender(WorkloadShedderOnQueue.class);

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
