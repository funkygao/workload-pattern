package io.github.workload.mock;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class MockService {
    public static final AtomicInteger counter = new AtomicInteger(0);

    public String hello(String whom) {
        counter.incrementAndGet();
        try {
            int ms = ThreadLocalRandom.current().nextInt(40);
            TimeUnit.MILLISECONDS.sleep(ms);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return whom;
    }
}
