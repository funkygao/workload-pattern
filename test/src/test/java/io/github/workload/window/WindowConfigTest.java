package io.github.workload.window;

import org.junit.jupiter.api.Test;

import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.*;

class WindowConfigTest {
    static final WindowRolloverStrategy<CountWindowState> countRolloverStrategy = new CountRolloverStrategy();
    static final BiConsumer<Long, CountWindowState> countDummyOnRollover = (nowNs, state) -> { };
    static final WindowRolloverStrategy<CountAndTimeWindowState> countAndTimeRolloverStrategy = new CountAndTimeRolloverStrategy();
    static final BiConsumer<Long, CountAndTimeWindowState> countAndTimeDummyOnRollover = (nowNs, state) -> { };

    @Test
    void genericsConsistentAndSafe() {
        // 如下代码，没有使用一致的泛型类型，compile error
        // 1. 入参S一致，但返回值T不一致
        //WindowConfig<CountAndTimeWindowState> config = WindowConfig.create(countRolloverStrategy, countDummyOnRollover);
        // 2. 两个入参S不一致
        //WindowConfig.create(countAndTimeRolloverStrategy, countDummyOnRollover);
        //WindowConfig.create(countRolloverStrategy, countAndTimeDummyOnRollover);

        WindowConfig<CountAndTimeWindowState> config1 = WindowConfig.create(countAndTimeRolloverStrategy, countAndTimeDummyOnRollover);
        WindowConfig<CountWindowState> config2 = WindowConfig.create(countRolloverStrategy, countDummyOnRollover);
    }

    @Test
    void defaults() {
        assertEquals(1_000_000, WindowConfig.NS_PER_MS);
    }

    @Test
    void testToSting() {
        WindowConfig<CountWindowState> config = WindowConfig.create(countRolloverStrategy, countDummyOnRollover);
        assertEquals("WindowConfig(time=1s,count=2048)", config.toString());
        config = WindowConfig.create(1, 129, countRolloverStrategy, countDummyOnRollover);
        assertEquals("WindowConfig(time=0s,count=129)", config.toString());

        WindowConfig<CountAndTimeWindowState> config1 = WindowConfig.create(countAndTimeRolloverStrategy, countAndTimeDummyOnRollover);
        assertEquals("WindowConfig(time=1s,count=2048)", config1.toString());
    }

    @Test
    void createWindowState_CountWindowState() {
        WindowConfig<CountWindowState> config = WindowConfig.create(countRolloverStrategy, countDummyOnRollover);
        CountWindowState state = config.createWindowState(0);
        assertEquals(0, state.requested());
        assertTrue(state.tryAcquireRolloverLock());
        assertFalse(state.tryAcquireRolloverLock());
    }

    @Test
    void createWindowState_CountAndTimeWindowState() {
        WindowConfig<CountAndTimeWindowState> config = WindowConfig.create(countAndTimeRolloverStrategy, countAndTimeDummyOnRollover);
        CountAndTimeWindowState state = config.createWindowState(5);
        CountAndTimeWindowState state1 = config.createWindowState(5);
        assertNotSame(state1, state);
        assertEquals(5, state.getStartNs());
        assertEquals(0, state.requested());
        assertTrue(state.tryAcquireRolloverLock());
        assertFalse(state.tryAcquireRolloverLock());
        assertEquals(0, state.admitted());
        assertEquals(0, state.avgQueuedMs());
        assertEquals(0, state.histogram().size());
    }

}