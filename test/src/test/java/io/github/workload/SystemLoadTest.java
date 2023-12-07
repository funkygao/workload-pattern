package io.github.workload;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class SystemLoadTest {
    private static final Logger log = LoggerFactory.getLogger(SystemLoadTest.class);

    @Test
    @Disabled
    void basic() throws InterruptedException {
        for (int i = 0; i < 20; i++) {
            log.info("usage:{} load avg:{}", SystemLoad.cpuUsage() * 100, SystemLoad.loadAverage());
            Thread.sleep(500);
        }
    }

}