package io.github.workload.window;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WindowConfigTest {

    @Test
    void testToSting() {
        WindowConfig config = new WindowConfig(null, null);
        assertEquals("WindowConfig(time=1s,count=2048)", config.toString());
    }

    @Test
    void setWithSystemProperties() {
        System.setProperty("workload.window.DEFAULT_TIME_CYCLE_MS", "50000000");
        WindowConfig config = new WindowConfig(null, null);
        assertEquals((long) 50000000 * 1000 * 1000, config.getTimeCycleNs());
        assertEquals("WindowConfig(time=50000s,count=2048)", config.toString());
        System.setProperty("workload.window.DEFAULT_REQUEST_CYCLE", "2");
        config = new WindowConfig(null, null);
        // 必须在WindowConfig类初始化前System.setProperty才有效，这里虽然设置2，但仍以2048为准
        assertEquals("WindowConfig(time=50000s,count=2048)", config.toString());
    }

    @Test
    void setWithSystemProperties1() {
        System.setProperty("workload.window.DEFAULT_REQUEST_CYCLE", "2");
        WindowConfig config = new WindowConfig(null, null);
        assertEquals("WindowConfig(time=1s,count=2)", config.toString());
    }

}