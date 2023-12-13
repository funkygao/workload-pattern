package io.github.workload.overloading;

import io.github.workload.SystemLoadProvider;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class SystemLoadTest {
    private static final Logger log = LoggerFactory.getLogger(SystemLoadTest.class);

    SystemLoadProvider loadProvider = SystemLoad.getInstance();

    @Test
    @Disabled
    void basic() throws InterruptedException {
        for (int i = 0; i < 20; i++) {
            log.info("usage:{} load avg:{}", loadProvider.cpuUsage() * 100, SystemLoad.loadAverage());
            Thread.sleep(500);
        }
    }

}