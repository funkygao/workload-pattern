package io.github.workload.metrics;

import io.github.workload.BaseConcurrentTest;
import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ThreadLocalRandom;

class SlidingWindowTest extends BaseConcurrentTest {

    @Test
    void demo() {
        setLogLevel(Level.TRACE);
        SimpleErrorSlidingWindow slidingWindow = new SimpleErrorSlidingWindow(5, 1000);
        for (int i = 0; i < 20; i++) {
            long time = i * 100;
            slidingWindow.currentWindow(time);
            slidingWindow.currentWindow(time + ThreadLocalRandom.current().nextInt(300));
        }
    }

}