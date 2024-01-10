package io.github.workload.window;

import com.google.common.collect.ImmutableMap;
import io.github.workload.BaseConcurrentTest;
import io.github.workload.helper.RandomUtil;
import io.github.workload.WorkloadPriority;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

class TumblingWindowTest extends BaseConcurrentTest {
    private static TumblingWindow window;

    @BeforeAll
    static void init() {
        System.setProperty("workload.window.DEFAULT_REQUEST_CYCLE", "100");
        Logger log = LoggerFactory.getLogger(TumblingWindowTest.class);
        WindowConfig<CountAndTimeWindowState> config = WindowConfig.create(new CountAndTimeRolloverStrategy() {
            @Override
            public void onRollover(long nowNs, CountAndTimeWindowState state, TumblingWindow<CountAndTimeWindowState> window) {
                log.info("onRollover, requested:{} window:{}", state.requested(), state.hashCode());
                try {
                    // simulate adjust admission level overhead
                    Thread.sleep(ThreadLocalRandom.current().nextInt(20));
                } catch (InterruptedException e) {
                }
            }
        });
        window = new TumblingWindow(config, "test", System.nanoTime());
    }

    @RepeatedTest(1)
    void advance() {
        Map<Integer, Integer> P2Requests = ImmutableMap.of(
                5, 2,
                10, 20,
                20, 900,
                40, 320,
                41, 58,
                58, 123
        );
        log.info("initial window:{}", window.current().hashCode());
        inject(P2Requests);
    }

    void inject(Map<Integer, Integer> P2Requests) {
        AtomicInteger i = new AtomicInteger(0);
        Runnable task = () -> {
            List<Integer> priorities = new ArrayList<>(P2Requests.keySet());
            Collections.shuffle(priorities);
            for (int P : priorities) {
                for (int request = 0; request < P2Requests.get(P); request++) {
                    WorkloadPriority priority = WorkloadPriority.fromP(P);
                    int n = i.incrementAndGet();
                    int hash1 = window.current().hashCode();
                    window.advance(priority, RandomUtil.randomTrue(), System.nanoTime());
                    int hash2 = window.current().hashCode();
                    if (false && hash1 != hash2) {
                        log.info("n:{} window changed:{} -> {}", n, hash1, hash2);
                    }
                }
            }
        };
        concurrentRun(task);
    }

    @Test
    @Disabled
    void gcPressureOnCleanup() {
        final int requestCycle = 10000;
        WindowConfig<CountAndTimeWindowState> config = WindowConfig.create(1000 * WindowConfig.NS_PER_MS, requestCycle, new CountAndTimeRolloverStrategy() {
            @Override
            public void onRollover(long nowNs, CountAndTimeWindowState state, TumblingWindow<CountAndTimeWindowState> window) {

            }
        });
        window = new TumblingWindow(config, "test", System.nanoTime());

        // 并发注入大量请求，导致频繁的窗口切换，查看GC压力
        // TODO 比较窗口切换时，不/调用 cleanup 的差异，执行时关闭日志
        Runnable task = () -> {
            int N = ThreadLocalRandom.current().nextInt(1 << 30);
            log.info("{} requests will be injected", N);
            for (int request = 0; request < N; request++) {
                window.advance(RandomUtil.randomWorkloadPriority(), true, System.nanoTime());
            }
            log.info("done");
        };
        concurrentRun(task);
    }

}
