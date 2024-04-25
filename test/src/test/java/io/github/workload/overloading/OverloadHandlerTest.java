package io.github.workload.overloading;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OverloadHandlerTest {
    private final OverloadHandler overloadHandler = new OverloadHandler();

    @Test
    void basic() {
        final String service = "com.google.search.SearchApi";
        final double budget = 0.1; // 10%
        for (int i = 0; i < 20; i++) {
            overloadHandler.sendRequest(service);
        }

        // 第21个请求，服务器响应为：overload
        overloadHandler.sendRequest(service);
        assertTrue(overloadHandler.attemptRetry(service, budget));
        assertTrue(overloadHandler.attemptRetry(service, budget));
        assertFalse(overloadHandler.attemptRetry(service, budget));

        final String baidu = "baidu";
        assertFalse(overloadHandler.attemptRetry(baidu, budget));
        overloadHandler.sendRequest(baidu);
        assertTrue(overloadHandler.attemptRetry(baidu, budget));
        assertFalse(overloadHandler.attemptRetry(baidu, budget));
    }
}
