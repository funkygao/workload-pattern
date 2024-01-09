package io.github.workload.window;

import io.github.workload.helper.RandomUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CountRolloverStrategyTest {
    private final WindowRolloverStrategy<CountWindowState> strategy = new CountRolloverStrategy() {
        @Override
        public void onRollover(long nowNs, CountWindowState state, TumblingWindow<CountWindowState> window) {

        }
    };


    @Test
    void shouldRollover() {
        int N = 2;
        WindowConfig<CountWindowState> config = WindowConfig.create(0, N, WindowConfigTest.countRolloverStrategy);
        CountWindowState state = new CountWindowState();
        assertFalse(strategy.shouldRollover(state, System.nanoTime(), config));
        for (int i = 0; i < N; i++) {
            state.sample(RandomUtil.randomWorkloadPriority(), true);
        }
        assertTrue(strategy.shouldRollover(state, System.nanoTime(), config));
        assertEquals(N, state.requested());
    }

}