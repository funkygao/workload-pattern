package io.github.workload.overloading;

import io.github.workload.Sysload;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ContainerLoadTest {
    private static final Logger log = LoggerFactory.getLogger(ContainerLoadTest.class);

    Sysload loadProvider = new ContainerLoad(0);

    @RepeatedTest(10)
    @Execution(ExecutionMode.CONCURRENT)
    @Disabled
    void basic() throws InterruptedException {
        for (int i = 0; i < 20; i++) {
            log.info("usage:{}%", loadProvider.cpuUsage() * 100);
            Thread.sleep(500);
        }
    }

}