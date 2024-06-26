package io.github.workload.overloading;

import com.google.common.collect.ImmutableMap;
import io.github.workload.BaseTest;
import io.github.workload.WorkloadPriority;
import io.github.workload.metrics.tumbling.CountAndTimeRolloverStrategy;
import io.github.workload.metrics.tumbling.CountAndTimeWindowState;
import io.github.workload.metrics.tumbling.TumblingWindow;
import io.github.workload.metrics.tumbling.WindowConfig;
import io.github.workload.simulate.WorkloadPrioritySimulator;
import one.profiler.AsyncProfiler;
import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class FairShedderTest extends BaseTest {

    @Test
    void ConcurrentSkipListMap_headMap() {
        ConcurrentSkipListMap<Integer, String> histogram = new ConcurrentSkipListMap<>();
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
        assertThrows(NoSuchElementException.class, () -> {
            histogram.headMap(-1, true).descendingKeySet().iterator().next();
        });
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
        ConcurrentSkipListMap<Integer, String> histogram = new ConcurrentSkipListMap<>();
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

    @Test
    void relu_cpu_load() {
        AdmissionControllerFactory.resetForTesting();
        FairSafeAdmissionController admissionController = (FairSafeAdmissionController) AdmissionController.getInstance("RPC");
        final FairShedder shedder = admissionController.fairQueue();

        final double cpuThreshold = 0.8 * 100;
        for (double cpuUsage : new double[]{0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1}) {
            cpuUsage *= 100;
            log.info("cpu relu({}): {}", cpuUsage, shedder.relu(cpuUsage, cpuThreshold));
        }
    }

    @Test
    void relu_watermark() {
        AdmissionControllerFactory.resetForTesting();
        FairSafeAdmissionController admissionController = (FairSafeAdmissionController) AdmissionController.getInstance("RPC");
        final FairShedder shedder = admissionController.fairQueue();

        for (double lastWindowShedRatio : new double[]{0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8}) {
            lastWindowShedRatio *= 100;
            log.info("shed relu({}): {}", lastWindowShedRatio, shedder.relu(lastWindowShedRatio, 60));
        }
    }

    /**
     * 单测自动跑出火焰图.
     *
     * @see <a href="https://github.com/async-profiler/async-profiler">Async Profiler</a>
     */
    @Test
    @Disabled("shows how to generate flame graph, generate jfr file")
    void flameGraphed(TestInfo testInfo) throws IOException {
        AsyncProfiler profiler = AsyncProfiler.getInstance(System.getenv("ASYNC_PROFILER_LIB"));
        final String fileName = "pf";
        profiler.execute(String.format("start,jfr,event=wall,file=%s.jfr", fileName));
        adaptWatermark_overloaded_false_true(testInfo);
        profiler.execute(String.format("stop,file=%s.jfr", fileName));
    }

    @RepeatedTest(1)
    @DisplayName("模拟过载后的 workload shed")
    void adaptWatermark_overloaded_false_true(TestInfo testInfo) {
        log.info("{}", testInfo.getDisplayName());

        // 为了排除运行速度的影响，把窗口的时间周期放大：只以请求次数为周期
        System.setProperty("workload.window.DEFAULT_TIME_CYCLE_MS", "50000000");

        setLogLevel(Level.INFO);

        AdmissionControllerFactory.resetForTesting();
        FairSafeAdmissionController admissionController = (FairSafeAdmissionController) AdmissionController.getInstance("RPC");
        final FairShedder shedder = admissionController.fairQueue();
        // 刚启动时，P为最大值(最低优先级)
        final int initialP = shedder.watermark().P();
        assertEquals(initialP, shedder.watermark().P());
        assertEquals(WorkloadPriority.MAX_P, initialP);

        WorkloadPrioritySimulator generator = new WorkloadPrioritySimulator().simulateFullyRandomWorkloadPriority(50);
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
            shedder.predictWatermark(currentWindow, 1, System.nanoTime());
            assertEquals(initialP, shedder.watermark().P());
        }

        log.info("显式过载，看看调整到哪个优先级。当前窗口，histogram size:{}, {}", currentWindow.histogram().size(), currentWindow.histogram());
        WorkloadPriority lastLevel = shedder.watermark();
        for (int i = 0; i < (1 / FairShedder.DROP_RATE_BASE); i++) {
            shedder.predictWatermark(currentWindow, 0.5, System.nanoTime());
            log.debug("adapted {}: {} -> {}", lastLevel.P() - shedder.watermark().P(), lastLevel, shedder.watermark());
            if (lastLevel.equals(shedder.watermark())) {
                log.info("admission level cannot be adapted any more, STOPPED!");
                break;
            }

            // 每次调整一定是 admission level 更优先的：提升门槛值
            assertTrue(shedder.watermark().P() <= lastLevel.P());
            lastLevel = shedder.watermark();
        }

        generator.reset().simulateFewWorkloadPriority();
        shedder.resetForTesting();
        for (Map.Entry<WorkloadPriority, Integer> entry : generator) {
            for (int i = 0; i < entry.getValue(); i++) {
                assertTrue(shedder.admit(entry.getKey()));
            }
        }
        final CountAndTimeWindowState currentWindow1 = shedder.currentWindow();
        log.info("模拟当前窗口请求量低于100的场景 histogram:{}", currentWindow1.histogram());
        shedder.predictWatermark(currentWindow1, 0.6, System.nanoTime());
        shedder.predictWatermark(currentWindow1, 0.6, System.nanoTime());

        shedder.resetForTesting();
        final CountAndTimeWindowState currentWindow2 = shedder.currentWindow();
        log.info("模拟当前窗口1个请求都没有的场景 histogram:{}", currentWindow2.histogram());
        shedder.predictWatermark(currentWindow2, 0.6, System.nanoTime());
        shedder.predictWatermark(currentWindow2, 0.6, System.nanoTime());
    }

    @RepeatedTest(2)
    @Execution(ExecutionMode.SAME_THREAD)
    @DisplayName("先过载，再恢复")
    void adaptWatermark_shedMore_then_admitMore(TestInfo testInfo) {
        log.info("{}", testInfo.getDisplayName());

        System.setProperty("workload.window.DEFAULT_TIME_CYCLE_MS", "50000000");
        setLogLevel(Level.INFO);
        AdmissionControllerFactory.resetForTesting();
        FairSafeAdmissionController admissionController = (FairSafeAdmissionController) AdmissionController.getInstance("RPC");
        final FairShedder shedder = admissionController.fairQueue();

        WorkloadPrioritySimulator generator = new WorkloadPrioritySimulator().simulateFullyRandomWorkloadPriority(10);
        for (Map.Entry<WorkloadPriority, Integer> entry : generator) {
            for (int i = 0; i < entry.getValue(); i++) {
                shedder.admit(entry.getKey());
            }
        }

        List<Integer> shedHistory = new LinkedList<>();
        final CountAndTimeWindowState currentWindow = shedder.currentWindow();
        log.info("histogram:size:{}, {}", currentWindow.histogram().size(), currentWindow.histogram());
        WorkloadPriority lastLevel = shedder.watermark();
        shedHistory.add(lastLevel.P());
        log.info("overloaded, shed more...");
        int sheddingTimes = 0;
        for (int i = 0; i < (1 / FairShedder.DROP_RATE_BASE); i++) {
            // 已过载
            shedder.predictWatermark(currentWindow, 0.6, System.nanoTime());
            log.debug("adapted {}: {} -> {}", lastLevel.P() - shedder.watermark().P(), lastLevel, shedder.watermark());
            if (lastLevel.equals(shedder.watermark())) {
                sheddingTimes = i + 1;
                log.info("cannot shed any more:{}", sheddingTimes);
                break;
            }

            if (i == (1 / FairShedder.DROP_RATE_BASE) - 1) {
                sheddingTimes = i + 1;
                log.info("drained, stop shed any more:{}", sheddingTimes);
            }

            // 每次调整一定是 admission level 更优先的：提升门槛值
            assertTrue(shedder.watermark().P() <= lastLevel.P());
            lastLevel = shedder.watermark();
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
            shedder.predictWatermark(currentWindow, 1, System.nanoTime());
            if (lastLevel.equals(shedder.watermark())) {
                admittingTimes = i + 1;
                log.info("cannot admit any more:{}", admittingTimes);
                break;
            }

            // 每次调整一定是 admission level 更优先的：提升门槛值
            assertTrue(shedder.watermark().P() >= lastLevel.P());
            lastLevel = shedder.watermark();
            admitHistory.add(lastLevel.P());
            everAdmitted = true;
        }

        if (everAdmitted) {
            // 完全恢复
            assertEquals(WorkloadPriority.MAX_P, shedder.watermark().P());
        }

        log.info("shed:{}, admit:{}", shedHistory, admitHistory);

        if (currentWindow.admitted() > 99) {
            if (sheddingTimes == 0) {
                assertEquals(0, admittingTimes);
            } else if (sheddingTimes == 1) {
                assertEquals(1, admittingTimes);
            } else {
                //assertTrue(admittingTimes > 2 * sheddingTimes, String.format("%d %d", sheddingTimes, admittingTimes));
            }
        }
    }

    @RepeatedTest(1)
    void simulateRpc() {
        FairSafeAdmissionController admissionController = (FairSafeAdmissionController) AdmissionController.getInstance("RPC");
        FairShedder shedder = admissionController.fairQueue();
        WorkloadPrioritySimulator generator = new WorkloadPrioritySimulator().simulateRpcWorkloadPriority(1 << 20);
        for (Map.Entry<WorkloadPriority, Integer> entry : generator) {
            for (int i = 0; i < entry.getValue(); i++) {
                shedder.admit(entry.getKey());
            }
        }
    }

    @RepeatedTest(1)
    void simulateMixedScenario() {
        FairSafeAdmissionController admissionController = (FairSafeAdmissionController) AdmissionController.getInstance("RPC");
        FairShedder shedder = admissionController.fairQueue();
        WorkloadPrioritySimulator generator = new WorkloadPrioritySimulator().simulateMixedWorkloadPriority(1 << 20);
        for (Map.Entry<WorkloadPriority, Integer> entry : generator) {
            for (int i = 0; i < entry.getValue(); i++) {
                shedder.admit(entry.getKey());
            }
        }
    }

    @RepeatedTest(10)
    @DisplayName("固定请求分布的并发测试")
    void adaptWatermark_dropMore_unbalanced(TestInfo testInfo) throws InterruptedException {
        //System.setProperty("workload.window.DEFAULT_TIME_CYCLE_MS", "50000000");
        FairSafeAdmissionController admissionController = (FairSafeAdmissionController) AdmissionController.getInstance("RPC");
        FairShedder shedder = admissionController.fairQueue();
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
        shedder.predictWatermark(shedder.currentWindow(), 0.6, System.nanoTime());
    }

    private void injectWorkloads(FairShedder shedder, Map<Integer, Integer> P2Requests) {
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

    @Test
    void predictWatermark_penalizeFutureLowPriorities() {
        WindowConfig<CountAndTimeWindowState> config = WindowConfig.create(TimeUnit.HOURS.toNanos(1), 1 << 20,
                new CountAndTimeRolloverStrategy() {
                    @Override
                    public void onRollover(long nowNs, CountAndTimeWindowState snapshot, TumblingWindow<CountAndTimeWindowState> window) {
                    }
                }
        );

        TumblingWindow<CountAndTimeWindowState> window = new TumblingWindow<>(config, "unit_test", System.nanoTime());
        List<PredictFixture> fixtures = Arrays.asList(
                new PredictFixture(0, 0.8, false),
                new PredictFixture(10, 0.8, false),
                new PredictFixture(19, 1.0, false),
                new PredictFixture(19, 0.8, true),
                new PredictFixture(16, 0.8, true),
                new PredictFixture(16, 1, false),
                new PredictFixture(20, 0.8, true),
                new PredictFixture(100, 0.8, true));

        for (PredictFixture fixture : fixtures) {
            WorkloadPrioritySimulator generator = new WorkloadPrioritySimulator().simulateMixedWorkloadPriority(fixture.N);
            for (Map.Entry<WorkloadPriority, Integer> entry : generator) {
                for (int i = 0; i < entry.getValue(); i++) {
                    window.advance(entry.getKey());
                }
            }

            FairShedder shedder = new FairShedder("unit_test") {
                @Override
                protected double overloadGradient(long nowNs, CountAndTimeWindowState snapshot) {
                    return 0;
                }
            };
            WorkloadPriority watermarkBefore = shedder.watermark();
            shedder.predictWatermark(window.current(), fixture.grad, System.nanoTime());
            WorkloadPriority watermarkAfter = shedder.watermark();
            if (fixture.watermarkChange) {
                assertNotEquals(watermarkBefore, watermarkAfter);
            } else {
                assertEquals(watermarkBefore, watermarkAfter, fixture.N + ":" + fixture.grad);
            }
            window.resetForTesting();
        }
    }

    static class PredictFixture {
        final int N;
        final double grad;
        final boolean watermarkChange;

        PredictFixture(int N, double grad, boolean watermarkChange) {
            this.N = N;
            this.grad = grad;
            this.watermarkChange = watermarkChange;
        }
    }

}
