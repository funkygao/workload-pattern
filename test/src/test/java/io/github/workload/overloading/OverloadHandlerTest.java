package io.github.workload.overloading;

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OverloadHandlerTest {
    private final OverloadHandler overloadHandler = new OverloadHandler(1);

    @Test
    void basic() throws InterruptedException {
        final String service = "com.google.search.SearchApi";
        final double budget = 0.1; // 10%
        for (int i = 0; i < 20; i++) {
            overloadHandler.sendRequest(service);
        }

        // 第21个请求，服务器响应为：overload
        overloadHandler.sendRequest(service);
        assertTrue(overloadHandler.attemptRetry(service, budget));
        assertTrue(overloadHandler.attemptRetry(service, budget));
        assertTrue(overloadHandler.attemptRetry(service, budget));
        assertFalse(overloadHandler.attemptRetry(service, budget));
        TimeUnit.MILLISECONDS.sleep(1200); // trigger reset counters
        assertFalse(overloadHandler.attemptRetry(service, budget));
        overloadHandler.sendRequest(service);
        assertTrue(overloadHandler.attemptRetry(service, budget));

        final String baidu = "baidu";
        assertFalse(overloadHandler.attemptRetry(baidu, budget)); // 因为此时还没有请求
        overloadHandler.sendRequest(baidu);
        assertTrue(overloadHandler.attemptRetry(baidu, budget)); // 因为此时还从来没有retry
        assertFalse(overloadHandler.attemptRetry(baidu, budget));
    }
}
