package io.github.workload;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class NamedThreadFactoryTest {

    @Test
    void basic() throws InterruptedException {
        AtomicReference<String> name = new AtomicReference<>();
        ThreadFactory factory = new NamedThreadFactory("foo");
        Thread thread = factory.newThread(() -> {
            name.set(Thread.currentThread().getName());
        });
        thread.start();
        Thread.sleep(100); // wait for task execution
        assertEquals("foo-1", name.get());

        thread = factory.newThread(() -> {
            name.set(Thread.currentThread().getName());
        });
        thread.start();
        Thread.sleep(100); // wait for task execution
        assertEquals("foo-2", name.get());

        factory = new NamedThreadFactory("bar");
        thread = factory.newThread(() -> {
            name.set(Thread.currentThread().getName());
        });
        thread.start();
        Thread.sleep(100); // wait for task execution
        assertEquals("bar-1", name.get());
    }

}