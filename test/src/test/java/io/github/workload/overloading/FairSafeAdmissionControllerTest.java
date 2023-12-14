package io.github.workload.overloading;

import io.github.workload.SystemLoadProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.*;

class FairSafeAdmissionControllerTest {
    private static final Logger log = LoggerFactory.getLogger(FairSafeAdmissionControllerTest.class);

    @BeforeAll
    static void setUp() {
        System.setProperty("workload.window.DEFAULT_TIME_CYCLE_MS", "59");
        System.setProperty("workload.window.DEFAULT_REQUEST_CYCLE", "100");
    }

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
        WorkloadShedder detector = controller.shedderOnQueue;
        controller.overloaded();
        assertTrue(detector.isOverloaded(System.nanoTime()));
        Thread.sleep(1200); // 经过一个时间窗口
        assertFalse(detector.isOverloaded(System.nanoTime()));
    }

    @Test
    void simulateNeverOverload() {
        FairSafeAdmissionController mqController = (FairSafeAdmissionController) AdmissionController.getInstance("MQ");
        FairSafeAdmissionController rpcController = (FairSafeAdmissionController) AdmissionController.getInstance("RPC");
        FairSafeAdmissionController webController = (FairSafeAdmissionController) AdmissionController.getInstance("Web");
        FairSafeAdmissionController.shedderOnCpu.loadProvider = new AlwaysHealthySystemLoad();
        for (int i = 0; i < SamplingWindow.DEFAULT_REQUEST_CYCLE + 1; i++) {
            log.info("request: {}", i + 1);
            // 默认情况下，都放行：除非此时CPU已经高了
            WorkloadPriority mq = WorkloadPrioritizer.randomMQ();
            WorkloadPriority rpc = WorkloadPrioritizer.randomRpc();
            WorkloadPriority web = WorkloadPrioritizer.randomWeb();

            // 对于 CPU shedder，each iteration 3 times admit，因此 cpu overload至少要等 i=682/1s 后才进入保护
            // 只有 swap window 时，才会检查是否过载
            assertTrue(mqController.admit(mq));
            assertTrue(rpcController.admit(rpc));
            assertTrue(webController.admit(web));
        }

        // 没有过overload，level不会变
        AdmissionLevel admitAll = AdmissionLevel.ofAdmitAll();
        assertEquals(admitAll, FairSafeAdmissionController.shedderOnCpu.getAdmissionLevel());
        assertEquals(admitAll, mqController.shedderOnQueue.getAdmissionLevel());
        assertEquals(admitAll, rpcController.shedderOnQueue.getAdmissionLevel());
        assertEquals(admitAll, webController.shedderOnQueue.getAdmissionLevel());
    }

    @Test
    void simulateCpuRandomlyOverload() {
        FairSafeAdmissionController mqController = (FairSafeAdmissionController) AdmissionController.getInstance("MQ");
        FairSafeAdmissionController rpcController = (FairSafeAdmissionController) AdmissionController.getInstance("RPC");
        FairSafeAdmissionController webController = (FairSafeAdmissionController) AdmissionController.getInstance("Web");
        FairSafeAdmissionController.shedderOnCpu.loadProvider = new RandomCpuLoadProvider();
    }

    private static class RandomCpuLoadProvider implements SystemLoadProvider {

        @Override
        public double cpuUsage() {
            double cpuUsage = ThreadLocalRandom.current().nextDouble() + 0.5;
            log.info("cpu usage: {}", cpuUsage);
            return cpuUsage;
        }
    }

    private static class AlwaysHealthySystemLoad implements SystemLoadProvider {
        @Override
        public double cpuUsage() {
            return 0;
        }
    }


}

