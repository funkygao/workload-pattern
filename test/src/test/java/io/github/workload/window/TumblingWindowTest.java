package io.github.workload.window;

import com.google.common.collect.ImmutableMap;
import io.github.workload.AbstractBaseTest;
import io.github.workload.overloading.RandomUtil;
import io.github.workload.overloading.WorkloadPriority;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.RepeatedTest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

class TumblingWindowTest extends AbstractBaseTest {
    private static TumblingWindow window;

    @BeforeAll
    static void init() {
        System.setProperty("workload.window.DEFAULT_REQUEST_CYCLE", "100");
        Logger log = LoggerFactory.getLogger(TumblingWindowTest.class);
        WindowConfig config = new WindowConfig<TimeAndCountWindowState>(new TimeAndCountRolloverStrategy(),
                (nowNs, state) -> {
                    log.info("onWindowSwap, request:{} window:{}", state.requested(), state.hashCode());
                    try {
                        // simulate adjust admission level overhead
                        Thread.sleep(ThreadLocalRandom.current().nextInt(20));
                    } catch (InterruptedException e) {
                    }
                });
        window = new TumblingWindow(System.nanoTime(), "test", config);
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
                    window.advance(priority, RandomUtil.randomBoolean(), System.nanoTime());
                    int hash2 = window.current().hashCode();
                    if (false && hash1 != hash2) {
                        log.info("n:{} window changed:{} -> {}", n, hash1, hash2);
                    }
                }
            }
        };
        concurrentRun(task);
    }

}
