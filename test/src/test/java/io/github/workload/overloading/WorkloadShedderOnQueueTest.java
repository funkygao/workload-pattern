package io.github.workload.overloading;

import io.github.workload.BaseConcurrentTest;
import io.github.workload.SystemClock;
import io.github.workload.helper.PrioritizedRequestGenerator;
import io.github.workload.helper.RandomUtil;
import io.github.workload.window.WindowConfig;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WorkloadShedderOnQueueTest extends BaseConcurrentTest {

    @Test
    void isOverloaded() throws InterruptedException {
        WorkloadShedderOnQueue shedder = new WorkloadShedderOnQueue("test");
        // window cannot be null
        try {
            shedder.isOverloaded(System.nanoTime(), null);
            fail();
        } catch (NullPointerException expected) {
        }

        // 刚开始时，无论如何都不该过载
        assertFalse(shedder.isOverloaded(-1, shedder.currentWindow()));
        assertFalse(shedder.isOverloaded(0, shedder.currentWindow()));
        assertFalse(shedder.isOverloaded(Long.MAX_VALUE, shedder.currentWindow()));
        // 窗口内没有任何请求
        assertFalse(shedder.isOverloaded(System.nanoTime(), shedder.currentWindow()));

        // 显式过载
        shedder.overload(System.nanoTime());
        assertFalse(shedder.isOverloaded(0, shedder.currentWindow()));
        assertTrue(shedder.isOverloaded(System.nanoTime(), shedder.currentWindow()));
        Thread.sleep(WindowConfig.DEFAULT_TIME_CYCLE_NS / WindowConfig.NS_PER_MS + SystemClock.PRECISION_DRIFT_MS);
        // 超过窗口时间周期后，不再过载
        assertFalse(shedder.isOverloaded(System.nanoTime(), shedder.currentWindow()));
        assertFalse(shedder.isOverloaded(0, shedder.currentWindow()));
        shedder.overload(System.nanoTime());
        assertTrue(shedder.isOverloaded(System.nanoTime(), shedder.currentWindow()));
        shedder.resetForTesting();
        assertFalse(shedder.isOverloaded(System.nanoTime(), shedder.currentWindow()));

        PrioritizedRequestGenerator generator = new PrioritizedRequestGenerator().generateFullyRandom(15);
        for (Map.Entry<WorkloadPriority, Integer> entry : generator) {
            for (int i = 0; i < entry.getValue(); i++) {
                shedder.admit(entry.getKey()); // 会不停地
                if (false) {
                    // 模拟：一会儿过载，一会儿恢复
                    if (RandomUtil.randomBoolean(1)) {
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
        assertTrue(shedder.isOverloaded(System.nanoTime(), shedder.currentWindow()));
    }

}