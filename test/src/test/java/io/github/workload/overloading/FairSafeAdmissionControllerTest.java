package io.github.workload.overloading;

import io.github.workload.*;
import io.github.workload.helper.RandomUtil;
import io.github.workload.simulate.WorkloadPrioritizer;
import io.github.workload.metrics.tumbling.WindowConfig;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.*;

@Disabled
class FairSafeAdmissionControllerTest extends BaseConcurrentTest {

    @BeforeAll
    static void setUp() {
        System.setProperty("workload.window.DEFAULT_TIME_CYCLE_MS", "500");
        System.setProperty("workload.window.DEFAULT_REQUEST_CYCLE", "100");
    }

    @AfterAll
    static void tearDown() {
        System.clearProperty("workload.window.DEFAULT_TIME_CYCLE_MS");
        System.clearProperty("workload.window.DEFAULT_REQUEST_CYCLE");
    }

    @Test
    void basic() {
        AdmissionController controller = AdmissionController.getInstance("foo");
        assertThrows(NullPointerException.class, () -> {
            controller.admit(null);
        });
        assertTrue(controller.admit(Workload.ofPriority(WorkloadPriorityHelper.of(4, 6))));
        controller.feedback(AdmissionController.Feedback.ofQueuedNs(5 * 1000_000)); // 5ms
        controller.feedback(AdmissionController.Feedback.ofQueuedNs(10 * 1000_000)); // 10ms
    }

    @RepeatedTest(10)
    void simulateNeverOverload() {
        log.info("never overload start");
        FairSafeAdmissionController mqController = (FairSafeAdmissionController) AdmissionController.getInstance("MQ");
        FairSafeAdmissionController rpcController = (FairSafeAdmissionController) AdmissionController.getInstance("RPC");
        FairSafeAdmissionController webController = (FairSafeAdmissionController) AdmissionController.getInstance("Web");
        FairSafeAdmissionController.shedderOnCpu().setSysload(new AlwaysHealthySystemLoad());
        for (int i = 0; i < WindowConfig.DEFAULT_REQUEST_CYCLE + 1; i++) {
            log.info("loop: {}", i + 1);
            // 默认情况下，都放行：除非此时CPU已经高了
            WorkloadPriority mq = WorkloadPrioritizer.randomMQ();
            WorkloadPriority rpc = WorkloadPrioritizer.randomRpc();
            WorkloadPriority web = WorkloadPrioritizer.randomWeb();

            assertTrue(mqController.admit(Workload.ofPriority(mq)));
            assertTrue(rpcController.admit(Workload.ofPriority(rpc)));
            assertTrue(webController.admit(Workload.ofPriority(web)));
        }

        // 没有过overload，level不会变
        WorkloadPriority admitAll = WorkloadPriority.ofLowest();
        assertEquals(admitAll, FairSafeAdmissionController.shedderOnCpu().watermark());
        assertEquals(admitAll, mqController.shedderOnQueue().watermark());
        assertEquals(admitAll, rpcController.shedderOnQueue().watermark());
        assertEquals(admitAll, webController.shedderOnQueue().watermark());
    }

    private void simulateServiceRandomlyOverload(int sleepBound, Sysload sysload) throws InterruptedException {
        log.info("window(time:{}ms, count:{})", HyperParameter.getLong(HyperParameter.WINDOW_TIME_CYCLE_MS, 1000), HyperParameter.getLong(HyperParameter.WINDOW_REQUEST_CYCLE, 2 << 10));
        log.info("randomly overload start, random sleep bound:{}ms, cpu load:{}", sleepBound, sysload.getClass().getSimpleName());
        FairSafeAdmissionController mqController = (FairSafeAdmissionController) AdmissionController.getInstance("SQS");
        FairSafeAdmissionController rpcController = (FairSafeAdmissionController) AdmissionController.getInstance("RPC");
        FairSafeAdmissionController webController = (FairSafeAdmissionController) AdmissionController.getInstance("WEB");
        FairSafeAdmissionController.shedderOnCpu().setSysload(sysload);
        int loops = WindowConfig.DEFAULT_REQUEST_CYCLE * 5;
        for (int i = 0; i < loops; i++) {
            WorkloadPriority mq = WorkloadPrioritizer.randomMQ();
            WorkloadPriority rpc = WorkloadPrioritizer.randomRpc();
            WorkloadPriority web = WorkloadPrioritizer.randomWeb();
            log.trace("loop: {}, mq:{}, rpc:{}, web:{}", i + 1, mq, rpc, web);

            if (sleepBound > 0) {
                Thread.sleep(ThreadLocalRandom.current().nextInt(sleepBound));
            }

            // 随机制造局部过载
            if (RandomUtil.randomTrue(2)) {
                log.info("{}\uD83D\uDCA5 overload SQS ...", i);
                mqController.feedback(AdmissionController.Feedback.ofOverloaded());
            }
            if (RandomUtil.randomTrue(4)) {
                log.info("{}\uD83D\uDCA5 overload RPC ...", i);
                rpcController.feedback(AdmissionController.Feedback.ofOverloaded());
            }
            if (RandomUtil.randomTrue(1)) {
                log.info("{}\uD83D\uDCA5 overload WEB ...", i);
                webController.feedback(AdmissionController.Feedback.ofOverloaded());
            }

            // 对于 CPU shedder，each iteration 3 times admit，因此 cpu overload至少要等 i=682/1s 后才进入保护
            // 只有 swap window 时，才会检查是否过载
            if (!mqController.admit(Workload.ofPriority(mq))) {
                log.warn("{} SQS rejected {}", i, mq);
            }
            if (!rpcController.admit(Workload.ofPriority(rpc))) {
                log.warn("{} RPC rejected {}", i, rpc);
            }
            if (!webController.admit(Workload.ofPriority(web))) {
                log.warn("{} Web rejected {}", i, web);
            }
        }
    }

    @RepeatedTest(1)
    void simulateServiceRandomlyOverload() throws InterruptedException {
        simulateServiceRandomlyOverload(0, new AlwaysHealthySystemLoad());
    }

    @RepeatedTest(1)
    void simulateServiceRandomlyOverloadWithSleep() throws InterruptedException {
        simulateServiceRandomlyOverload(10, new AlwaysHealthySystemLoad());
    }

    @RepeatedTest(20)
    void simulateServiceAndCpuRandomlyOverload() throws InterruptedException {
        simulateServiceRandomlyOverload(60, new RandomCpuLoadProvider());
    }

    private static class RandomCpuLoadProvider implements Sysload {
        private static final Logger log = LoggerFactory.getLogger(RandomCpuLoadProvider.class);

        @Override
        public double cpuUsage() {
            double cpuUsage = ThreadLocalRandom.current().nextDouble() + 0.5;
            log.info("cpu usage: {}", cpuUsage);
            return cpuUsage;
        }
    }

    private static class AlwaysHealthySystemLoad implements Sysload {
        @Override
        public double cpuUsage() {
            return 0;
        }
    }

}

