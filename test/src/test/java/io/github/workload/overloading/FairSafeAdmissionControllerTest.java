package io.github.workload.overloading;

import io.github.workload.SystemLoadProvider;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.*;

class FairSafeAdmissionControllerTest {

    @Test
    void basic() {
        AdmissionController controller = AdmissionController.getInstance("foo");
        try {
            controller.admit(null);
            fail();
        } catch (NullPointerException expected) {
        }
        assertTrue(controller.admit(WorkloadPriority.of(4, 6)));
        controller.recordQueuedNs(5 * 1000_000); // 5ms
        controller.recordQueuedNs(10 * 1000_000); // 5ms
    }

    @Test
    void markOverloaded() throws InterruptedException {
        FairSafeAdmissionController controller = (FairSafeAdmissionController) AdmissionController.getInstance("foo");
        WorkloadShedder detector = controller.workloadShedderOnQueue;
        controller.overloaded();
        assertTrue(detector.isOverloaded(System.nanoTime()));
        Thread.sleep(1200); // 经过一个时间窗口
        assertFalse(detector.isOverloaded(System.nanoTime()));
    }

    @Test
    void simulate() throws InterruptedException {
        FairSafeAdmissionController mqController = (FairSafeAdmissionController) AdmissionController.getInstance("MQ");
        FairSafeAdmissionController rpcController = (FairSafeAdmissionController) AdmissionController.getInstance("RPC");
        FairSafeAdmissionController webController = (FairSafeAdmissionController) AdmissionController.getInstance("Web");
        FairSafeAdmissionController.workloadShedderOnCpu.loadProvider = new RandomCpuLoadProvider();
        for (int i = 0; i < 2001; i++) {
            // 默认情况下，都放行：除非此时CPU已经高了
            WorkloadPriority mq = WorkloadPrioritizer.randomMQ();
            WorkloadPriority rpc = WorkloadPrioritizer.randomRpc();
            WorkloadPriority web = WorkloadPrioritizer.randomWeb();

            assertTrue(mqController.admit(mq));
            assertTrue((rpcController.admit(rpc)));
            assertTrue(webController.admit(web));
        }

    }

    static class RandomCpuLoadProvider implements SystemLoadProvider {

        @Override
        public double cpuUsage() {
            return ThreadLocalRandom.current().nextDouble(100);
        }
    }

}

