package io.github.workload.overloading;

import io.github.workload.BaseConcurrentTest;
import io.github.workload.SystemClock;
import io.github.workload.WorkloadPriority;
import io.github.workload.simulate.WorkloadPrioritySimulator;
import io.github.workload.helper.RandomUtil;
import io.github.workload.metrics.tumbling.WindowConfig;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WorkloadShedderOnQueueTest extends BaseConcurrentTest {

    @Test
    void isOverloaded() throws InterruptedException {
        WorkloadShedderOnQueue shedder = new WorkloadShedderOnQueue("test");
        // window cannot be null
        assertThrows(NullPointerException.class, () -> {
            shedder.overloadGradient(System.nanoTime(), null);
        });

        // 刚开始时，无论如何都不该过载
        assertFalse(shedder.isOverloaded(shedder.overloadGradient(-1, shedder.currentWindow())));
        assertFalse(shedder.isOverloaded(shedder.overloadGradient(0, shedder.currentWindow())));
        assertFalse(shedder.isOverloaded(shedder.overloadGradient(Long.MAX_VALUE, shedder.currentWindow())));
        // 窗口内没有任何请求
        assertFalse(shedder.isOverloaded(shedder.overloadGradient(System.nanoTime(), shedder.currentWindow())));

        // 显式过载
        shedder.overload(System.nanoTime());
        assertFalse(shedder.isOverloaded(shedder.overloadGradient(0, shedder.currentWindow())));
        assertTrue(shedder.isOverloaded(shedder.overloadGradient(System.nanoTime(), shedder.currentWindow())));
        Thread.sleep(WindowConfig.DEFAULT_TIME_CYCLE_NS / WindowConfig.NS_PER_MS + SystemClock.PRECISION_DRIFT_MS);
        // 超过窗口时间周期后，不再过载
        assertFalse(shedder.isOverloaded(shedder.overloadGradient(System.nanoTime(), shedder.currentWindow())));
        assertFalse(shedder.isOverloaded(shedder.overloadGradient(0, shedder.currentWindow())));
        shedder.overload(System.nanoTime());
        assertTrue(shedder.isOverloaded(shedder.overloadGradient(System.nanoTime(), shedder.currentWindow())));
        shedder.resetForTesting();
        assertFalse(shedder.isOverloaded(shedder.overloadGradient(System.nanoTime(), shedder.currentWindow())));

        WorkloadPrioritySimulator generator = new WorkloadPrioritySimulator().simulateFullyRandomWorkloadPriority(15);
        for (Map.Entry<WorkloadPriority, Integer> entry : generator) {
            for (int i = 0; i < entry.getValue(); i++) {
                shedder.admit(entry.getKey()); // 会不停地
                if (false) {
                    // 模拟：一会儿过载，一会儿恢复
                    if (RandomUtil.randomTrue(1)) {
                        shedder.addWaitingNs(10 * WindowConfig.NS_PER_MS);
                    } else {
                        shedder.addWaitingNs((WorkloadShedderOnQueue.AVG_QUEUED_MS_UPPER_BOUND + 1) * WindowConfig.NS_PER_MS);
                    }
                } else {
                    shedder.addWaitingNs((WorkloadShedderOnQueue.AVG_QUEUED_MS_UPPER_BOUND + 1) * WindowConfig.NS_PER_MS);
                }
            }
        }
        log.info("should overloaded because avg queue time");
        assertTrue(shedder.isOverloaded(shedder.overloadGradient(System.nanoTime(), shedder.currentWindow())));
    }

    @Test
    void explicitOverloadGradient() {
        WorkloadShedderOnQueue shedder = new WorkloadShedderOnQueue("cpu");
        for (int i = 0; i < 20; i++) {
            final double gradient = shedder.explicitOverloadGradient();
            assertTrue(gradient < WorkloadShedder.GRADIENT_IDLE);
            assertTrue(gradient >= WorkloadShedder.GRADIENT_BUSIEST);
            assertTrue(shedder.isOverloaded(gradient));
            log.info("explicitOverloadGradient: {}", shedder.explicitOverloadGradient());
        }
    }

    @Test
    void queuingGradient() {
        WorkloadShedderOnQueue shedder = new WorkloadShedderOnQueue("queue");
        final int upperBound = 100;
        final int startPoint = 1;
        for (int i = 0; i < 100; i++) {
            final int queuedTime = startPoint + i;
            final double gradient = shedder.queuingGradient(i + startPoint, upperBound);
            assertFalse(shedder.isOverloaded(gradient));
        }

        for (int i = 100; i < 210; i++) {
            final int queuedTime = startPoint + i;
            final double gradient = shedder.queuingGradient(i + startPoint, upperBound);
            log.debug("{}/{} gradient:{}", queuedTime, upperBound, gradient);
            assertTrue(shedder.isOverloaded(gradient));
            assertTrue(gradient >= WorkloadShedder.GRADIENT_BUSIEST);
        }
    }

}