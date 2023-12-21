package io.github.workload.overloading;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ConcurrentSkipListMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkloadShedderTest {

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
        // trigger overload
        shedder.adaptAdmissionLevel(true);
        int drop = (int) (expectedAdmittedLastWindow * shedder.policy.getDropRate());
        final int expectedDrop = 95;
        assertEquals(expectedDrop, drop);
        assertEquals(initialP - expectedDrop, shedder.admissionLevel().P());
    }

    @Test
    void adaptAdmissionLevel_dropMore_() {

    }

}
