package io.github.workload.metrics;

import io.github.workload.BaseConcurrentTest;
import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.Test;

class SlidingWindowTest extends BaseConcurrentTest {

    @Test
    void demo() {
        setLogLevel(Level.TRACE);
        SimpleErrorSlidingWindow slidingWindow = new SimpleErrorSlidingWindow(5, 1000);
        for (int i = 0; i < 10; i++) {
            log.info("{}", slidingWindow.currentWindow(System.currentTimeMillis()));
        }
    }

}