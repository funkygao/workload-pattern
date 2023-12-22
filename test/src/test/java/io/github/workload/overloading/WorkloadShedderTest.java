package io.github.workload.overloading;

import com.google.common.collect.ImmutableMap;
import io.github.workload.AbstractBaseTest;
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

class WorkloadShedderTest extends AbstractBaseTest {

    @Test
    void ConcurrentSkipListMap() {
        ConcurrentSkipListMap<Integer, String> map = new ConcurrentSkipListMap();
        for (int i = 0; i < 10; i++) {
            map.put(i, "str");
        }

        assertEquals("[0, 1, 2, 3]", map.headMap(3, true).keySet().toString());
        assertEquals("[3, 2, 1, 0]", map.headMap(3, true).descendingKeySet().toString());
        assertEquals("[0, 1, 2]", map.headMap(3).keySet().toString());
        assertEquals("[5, 6, 7, 8, 9]", map.tailMap(5).keySet().toString());
    }

    @Test
    void adaptAdmissionLevel_dropMore_uniform_priority_histogram() {
        // 为了排除运行速度的影响，把窗口的时间周期放大：只以请求次数为周期
        System.setProperty("workload.window.DEFAULT_TIME_CYCLE_MS", "50000000");
        //System.setProperty("workload.window.DEFAULT_REQUEST_CYCLE", "2");

        FairSafeAdmissionController admissionController = (FairSafeAdmissionController) AdmissionController.getInstance("RPC");
        WorkloadShedder shedder = admissionController.shedderOnQueue;
        // 刚启动时，P为最大值(最低优先级)
        final int initialP = shedder.admissionLevel().P();
        assertEquals(initialP, shedder.admissionLevel().P());
        for (int P = 0; P < initialP; P++) {
            // 每个P发一个请求
            assertTrue(shedder.admit(WorkloadPriority.fromP(P)));
        }
        final int expectedAdmittedLastWindow = initialP % TumblingSampleWindow.DEFAULT_REQUEST_CYCLE;
        assertEquals(expectedAdmittedLastWindow, shedder.window.admitted());

        // 没有过载，调整admission level不变化
        shedder.adaptAdmissionLevel(false);
        assertEquals(initialP, shedder.admissionLevel().P());

        // trigger overload
        shedder.adaptAdmissionLevel(true);
        int drop = (int) (expectedAdmittedLastWindow * shedder.policy.getDropRate());
        final int expectedDrop = 95;
        assertEquals(expectedDrop, drop);
        assertEquals(initialP - expectedDrop, shedder.admissionLevel().P());
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
        shedder.adaptAdmissionLevel(true);
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
