package io.github.workload.overloading;

import com.google.common.collect.ImmutableMap;
import io.github.workload.BaseConcurrentTest;
import io.github.workload.WorkloadPriority;
import io.github.workload.helper.PrioritizedRequestGenerator;
import io.github.workload.window.CountAndTimeWindowState;
import io.github.workload.window.WindowConfig;
import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;

import static org.junit.jupiter.api.Assertions.*;

class WorkloadShedderTest extends BaseConcurrentTest {

    @Test
    void ConcurrentSkipListMap_headMap() {
        ConcurrentSkipListMap<Integer, String> histogram = new ConcurrentSkipListMap();
        assertFalse(histogram.headMap(1, true).descendingKeySet().iterator().hasNext());

        histogram.put(8, "");
        histogram.put(10, "");
        for (int i = 0; i <= 5; i++) {
            histogram.put(i, "");
        }

        // what kind of key is beyond histogram: {0=, 1=, 2=, 3=, 4=, 5=, 8=, 10=}
        // 0/10 within histogram
        assertTrue(histogram.headMap(0, true).descendingKeySet().iterator().hasNext());
        assertTrue(histogram.headMap(10, true).descendingKeySet().iterator().hasNext());
        // 9，该key不存在，但在histogram范围内
        assertTrue(histogram.headMap(9, true).descendingKeySet().iterator().hasNext());
        // 11，超过该范围，它取得的尾部key是10
        assertTrue(histogram.headMap(11, true).descendingKeySet().iterator().hasNext());
        Integer key = histogram.headMap(11, true).descendingKeySet().iterator().next();
        assertEquals(10, key);
        key = histogram.headMap(111, true).descendingKeySet().iterator().next();
        assertEquals(10, key);
        // -1, beyond histogram
        assertFalse(histogram.headMap(-1, true).descendingKeySet().iterator().hasNext());
        try {
            key = histogram.headMap(-1, true).descendingKeySet().iterator().next();
            fail();
        } catch (NoSuchElementException expected) {
        }
        // conclusion: headMap，只有给定的key超过的histogram最左侧值(exclusive)，iterator 才 !hasNext()


        // headMap里的toKey并不在map里，降序
        assertEquals("[10, 8, 5, 4, 3, 2, 1, 0]", histogram.headMap(100, true).descendingKeySet().toString());
        assertEquals("[10, 8, 5, 4, 3, 2, 1, 0]", histogram.headMap(100, false).descendingKeySet().toString());
        // edge case: 内部值，存在该值
        assertEquals("[8, 5, 4, 3, 2, 1, 0]", histogram.headMap(8, true).descendingKeySet().toString());
        // edge case: 内部值，不存在该值
        assertEquals("[5, 4, 3, 2, 1, 0]", histogram.headMap(7, true).descendingKeySet().toString());
        assertEquals("[5, 4, 3, 2, 1, 0]", histogram.headMap(6, true).descendingKeySet().toString());
        // edge case: 边界值，右侧
        assertEquals("[10, 8, 5, 4, 3, 2, 1, 0]", histogram.headMap(10, true).descendingKeySet().toString());
        assertEquals("[8, 5, 4, 3, 2, 1, 0]", histogram.headMap(10, false).descendingKeySet().toString());
        // edge case: 边界值，左侧
        assertEquals("[]", histogram.headMap(-1, true).descendingKeySet().toString());
        assertEquals("[]", histogram.headMap(-1, false).descendingKeySet().toString());
        assertEquals("[]", histogram.headMap(0, false).descendingKeySet().toString());
        assertEquals("[0]", histogram.headMap(0, true).descendingKeySet().toString());
        // keySet是升序
        assertEquals("[0, 1, 2, 3, 4, 5, 8, 10]", histogram.headMap(100, true).keySet().toString());

        assertEquals("[0, 1, 2, 3]", histogram.headMap(3, true).keySet().toString());
        assertEquals("[3, 2, 1, 0]", histogram.headMap(3, true).descendingKeySet().toString());
        // 默认是 not inclusive
        assertEquals(histogram.headMap(3), histogram.headMap(3, false));

        final Iterator<Map.Entry<Integer, String>> descendingEntries = histogram.headMap(5, true).descendingMap().entrySet().iterator();
        List<Integer> descendingKeys = new LinkedList<>();
        while (descendingEntries.hasNext()) {
            descendingKeys.add(descendingEntries.next().getKey());
        }
        assertEquals("[5, 4, 3, 2, 1, 0]", descendingKeys.toString());
    }

    @Test
    void ConcurrentSkipListMap_tailMap() {
        ConcurrentSkipListMap<Integer, String> histogram = new ConcurrentSkipListMap();
        histogram.put(8, "");
        histogram.put(10, "");
        for (int i = 0; i <= 5; i++) {
            histogram.put(i, "");
        }

        // what kind of key is beyond histogram: {0=, 1=, 2=, 3=, 4=, 5=, 8=, 10=}
        assertTrue(histogram.tailMap(10, true).keySet().iterator().hasNext());
        assertFalse(histogram.tailMap(10, false).keySet().iterator().hasNext());
        assertFalse(histogram.tailMap(11, false).keySet().iterator().hasNext());
        assertTrue(histogram.tailMap(-1, false).keySet().iterator().hasNext());
        assertTrue(histogram.tailMap(0, false).keySet().iterator().hasNext());

        assertEquals("[8, 10]", histogram.tailMap(5, false).keySet().toString());
    }

    @RepeatedTest(1)
    @DisplayName("模拟过载后的 workload shed")
    void adaptAdmissionLevel_overloaded_false_true(TestInfo testInfo) {
        log.info("{}", testInfo.getDisplayName());

        // 为了排除运行速度的影响，把窗口的时间周期放大：只以请求次数为周期
        System.setProperty("workload.window.DEFAULT_TIME_CYCLE_MS", "50000000");

        setLogLevel(Level.INFO);

        FairSafeAdmissionController.resetForTesting();
        FairSafeAdmissionController admissionController = (FairSafeAdmissionController) AdmissionController.getInstance("RPC");
        final WorkloadShedder shedder = admissionController.shedderOnQueue();
        // 刚启动时，P为最大值(最低优先级)
        final int initialP = shedder.admissionLevel().P();
        assertEquals(initialP, shedder.admissionLevel().P());
        assertEquals(WorkloadPriority.MAX_P, initialP);

        PrioritizedRequestGenerator generator = new PrioritizedRequestGenerator().generateFullyRandom(50);
        for (Map.Entry<WorkloadPriority, Integer> entry : generator) {
            for (int i = 0; i < entry.getValue(); i++) {
                assertTrue(shedder.admit(entry.getKey()));
            }
        }
        log.info("total requests: {}, window already rollover many times", generator.totalRequests());

        final CountAndTimeWindowState currentWindow = shedder.currentWindow();
        final int expectedAdmittedLastWindow = generator.totalRequests() % WindowConfig.DEFAULT_REQUEST_CYCLE;
        assertEquals(expectedAdmittedLastWindow, currentWindow.admitted());
        // 没有过载，因此：请求数=放行数
        assertEquals(currentWindow.requested(), currentWindow.admitted());

        log.info("没有过载，调整admission level不变化");
        for (int i = 0; i < 10; i++) {
            shedder.adaptAdmissionLevel(false, currentWindow);
            assertEquals(initialP, shedder.admissionLevel().P());
        }

        log.info("显式过载，看看调整到哪个优先级。当前窗口，histogram size:{}, {}", currentWindow.histogram().size(), currentWindow.histogram());
        AdmissionLevel lastLevel = shedder.admissionLevel();
        for (int i = 0; i < (1 / shedder.dropRate()); i++) {
            shedder.adaptAdmissionLevel(true, currentWindow);
            log.debug("adapted {}: {} -> {}", lastLevel.P() - shedder.admissionLevel().P(), lastLevel, shedder.admissionLevel());
            if (lastLevel.equals(shedder.admissionLevel())) {
                log.info("admission level cannot be adapted any more, STOPPED!");
                break;
            }

            // 每次调整一定是 admission level 更优先的：提升门槛值
            assertTrue(shedder.admissionLevel().P() <= lastLevel.P());
            lastLevel = shedder.admissionLevel();
        }

        generator.reset().generateFewRequests();
        shedder.resetForTesting();
        for (Map.Entry<WorkloadPriority, Integer> entry : generator) {
            for (int i = 0; i < entry.getValue(); i++) {
                assertTrue(shedder.admit(entry.getKey()));
            }
        }
        final CountAndTimeWindowState currentWindow1 = shedder.currentWindow();
        log.info("模拟当前窗口请求量低于100的场景 histogram:{}", currentWindow1.histogram());
        shedder.adaptAdmissionLevel(true, currentWindow1);
        shedder.adaptAdmissionLevel(true, currentWindow1);

        shedder.resetForTesting();
        final CountAndTimeWindowState currentWindow2 = shedder.currentWindow();
        log.info("模拟当前窗口1个请求都没有的场景 histogram:{}", currentWindow2.histogram());
        shedder.adaptAdmissionLevel(true, currentWindow2);
        shedder.adaptAdmissionLevel(true, currentWindow2);
    }

    @RepeatedTest(2)
    @Execution(ExecutionMode.SAME_THREAD)
    @DisplayName("先过载，再恢复")
    void adaptAdmissionLevel_shedMore_then_admitMore(TestInfo testInfo) {
        log.info("{}", testInfo.getDisplayName());

        System.setProperty("workload.window.DEFAULT_TIME_CYCLE_MS", "50000000");
        setLogLevel(Level.INFO);
        FairSafeAdmissionController.resetForTesting();
        FairSafeAdmissionController admissionController = (FairSafeAdmissionController) AdmissionController.getInstance("RPC");
        final WorkloadShedder shedder = admissionController.shedderOnQueue();

        PrioritizedRequestGenerator generator = new PrioritizedRequestGenerator().generateFullyRandom(10);
        for (Map.Entry<WorkloadPriority, Integer> entry : generator) {
            for (int i = 0; i < entry.getValue(); i++) {
                shedder.admit(entry.getKey());
            }
        }

        List<Integer> shedHistory = new LinkedList<>();
        final CountAndTimeWindowState currentWindow = shedder.currentWindow();
        log.info("histogram:size:{}, {}", currentWindow.histogram().size(), currentWindow.histogram());
        AdmissionLevel lastLevel = shedder.admissionLevel();
        shedHistory.add(lastLevel.P());
        log.info("overloaded, shed more...");
        int sheddingTimes = 0;
        for (int i = 0; i < (1 / shedder.dropRate()); i++) {
            // 已过载
            shedder.adaptAdmissionLevel(true, currentWindow);
            log.debug("adapted {}: {} -> {}", lastLevel.P() - shedder.admissionLevel().P(), lastLevel, shedder.admissionLevel());
            if (lastLevel.equals(shedder.admissionLevel())) {
                sheddingTimes = i + 1;
                log.info("cannot shed any more:{}", sheddingTimes);
                break;
            }

            if (i == (1 / shedder.dropRate()) - 1) {
                sheddingTimes = i + 1;
                log.info("drained, stop shed any more:{}", sheddingTimes);
            }

            // 每次调整一定是 admission level 更优先的：提升门槛值
            assertTrue(shedder.admissionLevel().P() <= lastLevel.P());
            lastLevel = shedder.admissionLevel();
            shedHistory.add(lastLevel.P());
        }

        log.info("not overloaded, admit more");
        //setLogLevel(Level.DEBUG);
        int admittingTimes = 0;
        List<Integer> admitHistory = new LinkedList<>();
        admitHistory.add(lastLevel.P());
        boolean everAdmitted = false;
        for (int i = 0; i < Integer.MAX_VALUE; i++) {
            // 未过载
            shedder.adaptAdmissionLevel(false, currentWindow);
            if (lastLevel.equals(shedder.admissionLevel())) {
                admittingTimes = i + 1;
                log.info("cannot admit any more:{}", admittingTimes);
                break;
            }

            // 每次调整一定是 admission level 更优先的：提升门槛值
            assertTrue(shedder.admissionLevel().P() >= lastLevel.P());
            lastLevel = shedder.admissionLevel();
            admitHistory.add(lastLevel.P());
            everAdmitted = true;
        }

        if (everAdmitted) {
            // 完全恢复
            assertEquals(WorkloadPriority.MAX_P, shedder.admissionLevel().P());
        }

        log.info("shed:{}, admit:{}", shedHistory, admitHistory);

        if (currentWindow.admitted() > 99) {
            if (sheddingTimes == 0) {
                assertEquals(0, admittingTimes);
            }else if (sheddingTimes==1) {
                assertEquals(1, admittingTimes);
            } else {
                //assertTrue(admittingTimes > 2 * sheddingTimes, String.format("%d %d", sheddingTimes, admittingTimes));
            }
        }
    }

    @RepeatedTest(1)
    void simulateRpc() {
        FairSafeAdmissionController admissionController = (FairSafeAdmissionController) AdmissionController.getInstance("RPC");
        WorkloadShedder shedder = admissionController.shedderOnQueue();
        PrioritizedRequestGenerator generator = new PrioritizedRequestGenerator().simulateRpcRequests(1 << 20);
        for (Map.Entry<WorkloadPriority, Integer> entry : generator) {
            for (int i = 0; i < entry.getValue(); i++) {
                shedder.admit(entry.getKey());
            }
        }
    }

    @RepeatedTest(1)
    void simulateMixedScenario() {
        FairSafeAdmissionController admissionController = (FairSafeAdmissionController) AdmissionController.getInstance("RPC");
        WorkloadShedder shedder = admissionController.shedderOnQueue();
        PrioritizedRequestGenerator generator = new PrioritizedRequestGenerator().simulateMixedRequests(1 << 20);
        for (Map.Entry<WorkloadPriority, Integer> entry : generator) {
            for (int i = 0; i < entry.getValue(); i++) {
                shedder.admit(entry.getKey());
            }
        }
    }

    @RepeatedTest(10)
    @DisplayName("固定请求分布的并发测试")
    void adaptAdmissionLevel_dropMore_unbalanced(TestInfo testInfo) throws InterruptedException {
        //System.setProperty("workload.window.DEFAULT_TIME_CYCLE_MS", "50000000");
        FairSafeAdmissionController admissionController = (FairSafeAdmissionController) AdmissionController.getInstance("RPC");
        WorkloadShedder shedder = admissionController.shedderOnQueue();
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
        shedder.adaptAdmissionLevel(true, shedder.currentWindow());
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
