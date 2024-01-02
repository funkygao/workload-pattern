package io.github.workload.overloading;

import com.google.common.collect.ImmutableMap;
import io.github.workload.BaseConcurrentTest;
import io.github.workload.window.CountAndTimeWindowState;
import io.github.workload.window.WindowConfig;
import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkloadShedderTest extends BaseConcurrentTest {

    @Test
    void ConcurrentSkipListMap_headMap() {
        ConcurrentSkipListMap<Integer, String> map = new ConcurrentSkipListMap();
        for (int i = 0; i <= 5; i++) {
            map.put(i, "");
        }
        map.put(8, "");
        map.put(10, "");


        // headMap里的toKey并不在map里，降序
        assertEquals("[10, 8, 5, 4, 3, 2, 1, 0]", map.headMap(100, true).descendingKeySet().toString());
        assertEquals("[10, 8, 5, 4, 3, 2, 1, 0]", map.headMap(100, false).descendingKeySet().toString());
        // edge case: 内部值，存在该值
        assertEquals("[8, 5, 4, 3, 2, 1, 0]", map.headMap(8, true).descendingKeySet().toString());
        // edge case: 内部值，不存在该值
        assertEquals("[5, 4, 3, 2, 1, 0]", map.headMap(7, true).descendingKeySet().toString());
        assertEquals("[5, 4, 3, 2, 1, 0]", map.headMap(6, true).descendingKeySet().toString());
        // edge case: 边界值，右侧
        assertEquals("[10, 8, 5, 4, 3, 2, 1, 0]", map.headMap(10, true).descendingKeySet().toString());
        assertEquals("[8, 5, 4, 3, 2, 1, 0]", map.headMap(10, false).descendingKeySet().toString());
        // edge case: 边界值，左侧
        assertEquals("[]", map.headMap(-1, true).descendingKeySet().toString());
        assertEquals("[]", map.headMap(-1, false).descendingKeySet().toString());
        assertEquals("[]", map.headMap(0, false).descendingKeySet().toString());
        assertEquals("[0]", map.headMap(0, true).descendingKeySet().toString());
        // keySet是升序
        assertEquals("[0, 1, 2, 3, 4, 5, 8, 10]", map.headMap(100, true).keySet().toString());

        assertEquals("[0, 1, 2, 3]", map.headMap(3, true).keySet().toString());
        assertEquals("[3, 2, 1, 0]", map.headMap(3, true).descendingKeySet().toString());
        // 默认是 not inclusive
        assertEquals(map.headMap(3), map.headMap(3, false));
    }

    @Test
    void ConcurrentSkipListMap_tailMap() {
        ConcurrentSkipListMap<Integer, String> map = new ConcurrentSkipListMap();
        for (int i = 0; i <= 9; i++) {
            // keys: [0, 1, 2, 3, 4, 5, 6, 7, 8, 9]
            map.put(i, "");
        }

        assertEquals("[5, 6, 7, 8, 9]", map.tailMap(5).keySet().toString());
    }

    @RepeatedTest(100)
    void adaptAdmissionLevel_overloaded_false_true() {
        // 为了排除运行速度的影响，把窗口的时间周期放大：只以请求次数为周期
        System.setProperty("workload.window.DEFAULT_TIME_CYCLE_MS", "50000000");

        setLogLevel(Level.INFO);

        FairSafeAdmissionController.reset();
        FairSafeAdmissionController admissionController = (FairSafeAdmissionController) AdmissionController.getInstance("RPC");
        WorkloadShedder shedder = admissionController.shedderOnQueue;
        // 刚启动时，P为最大值(最低优先级)
        final int initialP = shedder.admissionLevel().P();
        assertEquals(initialP, shedder.admissionLevel().P());
        assertEquals(WorkloadPriority.MAX_P, initialP);

        PrioritizedRequestGenerator generator = new PrioritizedRequestGenerator().fullyRandomize(50);
        for (int P : generator.priorities()) {
            final int requests = generator.requestsOfP(P);
            final WorkloadPriority priority = WorkloadPriority.fromP(P);
            for (int i = 0; i < requests; i++) {
                assertTrue(shedder.admit(priority));
            }
        }
        //setLogLevel(Level.DEBUG);
        log.info("total requests: {}, window already rollover many times", generator.totalRequests());

        CountAndTimeWindowState currentWindow = shedder.window.current();
        final int expectedAdmittedLastWindow = generator.totalRequests() % WindowConfig.DEFAULT_REQUEST_CYCLE;
        assertEquals(expectedAdmittedLastWindow, currentWindow.admitted());
        // 没有过载，因此：请求数=放行数
        assertEquals(currentWindow.requested(), currentWindow.admitted());

        log.info("没有过载，调整admission level不变化");
        shedder.adaptAdmissionLevel(false, currentWindow);
        assertEquals(initialP, shedder.admissionLevel().P());

        log.info("显式过载，看看调整到哪个优先级 histogram size:{}, {}", currentWindow.histogram().size(), currentWindow.histogram());
        AdmissionLevel lastLevel = shedder.admissionLevel();
        for (int i = 0; i < (1 / shedder.policy.getDropRate()); i++) {
            shedder.adaptAdmissionLevel(true, currentWindow);
            log.info("adapted {}: {} -> {}", lastLevel.P() - shedder.admissionLevel().P(), lastLevel, shedder.admissionLevel());
            if (lastLevel.equals(shedder.admissionLevel())) {
                log.info("admission level cannot be adapted any more");
                break;
            }

            // 每次调整一定是 admission level 更优先的：提升门槛值
            assertTrue(shedder.admissionLevel().P() <= lastLevel.P());
            lastLevel = shedder.admissionLevel();
        }

        // 模拟当前窗口请求量低于100的场景
    }

    @RepeatedTest(10)
    void adaptAdmissionLevel_dropMore_unbalanced(TestInfo testInfo) throws InterruptedException {
        //System.setProperty("workload.window.DEFAULT_TIME_CYCLE_MS", "50000000");
        FairSafeAdmissionController admissionController = (FairSafeAdmissionController) AdmissionController.getInstance("RPC");
        WorkloadShedder shedder = admissionController.shedderOnQueue;
        Map<Integer, Integer> P2Requests = ImmutableMap.of(
                5, 2,
                10, 20,
                20, 900,
                40, 320,
                41, 58,
                58, 123
        );

        log.info("{} {}", testInfo.getDisplayName(), shedder);

        injectWorkloads(shedder, P2Requests);
        shedder.adaptAdmissionLevel(true, shedder.window.current());
    }

    private void injectWorkloads(WorkloadShedder shedder, Map<Integer, Integer> P2Requests) {
        Runnable task = () -> {
            List<Integer> priorities = new ArrayList<>(P2Requests.keySet());
            Collections.shuffle(priorities);
            for (int P : priorities) {
                for (int request = 0; request < P2Requests.get(P); request++) {
                    WorkloadPriority priority = WorkloadPriority.fromP(P);
                    boolean admitted = shedder.admit(priority);
                    if (!admitted) {
                        log.trace("rejected: {}", priority);
                    }
                }
            }
        };
        concurrentRun(task);
    }

}
