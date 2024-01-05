package io.github.workload.window;

import io.github.workload.helper.RandomUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CountWindowStateTest {

    @Test
    void basic() {
        CountWindowState state = new CountWindowState();
        assertEquals(0, state.requested());
        for (int i = 0; i < 5; i++) {
            state.sample(RandomUtil.randomWorkloadPriority(), true);
        }
        assertEquals(5, state.requested());
    }

    @Test
    void tryAcquireRolloverLock() {
        CountWindowState state = new CountWindowState();
        assertTrue(state.tryAcquireRolloverLock());
        assertFalse(state.tryAcquireRolloverLock());
        assertFalse(state.tryAcquireRolloverLock());
    }

}