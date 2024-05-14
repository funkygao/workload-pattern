package io.github.workload.overloading;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.github.workload.Workload;
import io.github.workload.WorkloadPriority;
import io.github.workload.helper.LogUtil;
import io.github.workload.metrics.tumbling.TumblingWindow;
import io.github.workload.simulate.WorkloadPrioritizer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IntegrationTest {

    @AfterEach
    void tearDown() {
        AdmissionControllerFactory.resetForTesting();
    }

    void usageDemo() {
        AdmissionController ac = AdmissionController.getInstance("HTTP");
        // in servlet filter
        long requestReceived = System.nanoTime();
        WorkloadPriority priority = WorkloadPrioritizer.randomWeb();
        boolean ok = ac.admit(Workload.ofPriority(priority));
        ac.feedback(AdmissionController.Feedback.ofQueuedNs(System.nanoTime() - requestReceived));
        if (!ok) {
            // reject
        }
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

    @Test
    void basic() {

    }

}
