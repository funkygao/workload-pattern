package io.github.workload.metrics.tumbling;

import io.github.workload.helper.RandomUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CountAndTimeRolloverStrategyTest {
    private final WindowRolloverStrategy<CountAndTimeWindowState> strategy = new CountAndTimeRolloverStrategy() {
        @Override
        public void onRollover(long nowNs, CountAndTimeWindowState snapshot, TumblingWindow<CountAndTimeWindowState> window) {
            
        }
    };

    @Test
    void shouldRollover_byCount() {
        int N = 2;
        WindowConfig<CountAndTimeWindowState> config = WindowConfig.create(1000 * WindowConfig.NS_PER_MS, N, WindowConfigTest.countAndTimeRolloverStrategy);
        CountAndTimeWindowState state = new CountAndTimeWindowState(System.nanoTime());
        assertFalse(strategy.shouldRollover(state, System.nanoTime(), config));
        for (int i = 0; i < N; i++) {
            state.sample(RandomUtil.randomWorkloadPriority(), true);
        }
        assertEquals(N, state.requested());
        assertEquals(N, state.admitted());
        assertTrue(strategy.shouldRollover(state, System.nanoTime(), config));
    }

    @Test
    void shouldRollover_byTime() throws InterruptedException {
        // 100ms后滚动窗口
        WindowConfig<CountAndTimeWindowState> config = WindowConfig.create(1 * WindowConfig.NS_PER_MS, 1 << 20, WindowConfigTest.countAndTimeRolloverStrategy);
        CountAndTimeWindowState state = new CountAndTimeWindowState(System.nanoTime());
        assertFalse(strategy.shouldRollover(state, System.nanoTime(), config));
        Thread.sleep(10);
        assertTrue(strategy.shouldRollover(state, System.nanoTime(), config));
    }

}