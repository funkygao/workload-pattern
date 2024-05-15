package io.github.workload.tailtolerant;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.github.workload.helper.LogUtil;
import io.github.workload.mock.MockService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.RepeatedTest;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

class HedgedRequestTest {
    private final MockService mockService = new MockService();

    @AfterAll
    static void tearDown() {
        HedgedRequest.shutdown();
    }

    @AfterEach
    void resetCounter() {
        MockService.counter.set(0);
    }

    @RepeatedTest(10)
    @Disabled
    void demo() throws ExecutionException, InterruptedException, TimeoutException {
        ListAppender<ILoggingEvent> appender = LogUtil.setupAppender(HedgedRequest.class);

        final long deferMs = 20;
        final int N = 10;
        for (int i = 0; i < N; i++) {
            HedgedRequest<String> hedgedRequest = new HedgedRequest<>(deferMs);
            CompletableFuture<String> futureResult = hedgedRequest.call(() -> mockService.hello("ha"));
            String result = futureResult.get(1, TimeUnit.SECONDS);
            assertEquals("ha", result);
        }

        assertFalse(appender.list.isEmpty());
        assertTrue(appender.list.get(0).getFormattedMessage().contains("Original slow, hedged after " + deferMs + "ms"));
        assertTrue(MockService.counter.get() > N);
        assertEquals(N + appender.list.size(), MockService.counter.get());
        System.out.println(MockService.counter.get());
    }

}