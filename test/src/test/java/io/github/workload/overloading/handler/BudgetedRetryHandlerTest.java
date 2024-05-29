package io.github.workload.overloading.handler;

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BudgetedRetryHandlerTest {
    private final BudgetedRetryHandler budgetedRetryHandler = new BudgetedRetryHandler(1);

    @Test
    void basic() throws InterruptedException {
        final String service = "com.google.search.SearchApi";
        final double budget = 0.1; // 10%
        for (int i = 0; i < 20; i++) {
            budgetedRetryHandler.recordRequest(service);
        }

        // 第21个请求，服务器响应为：overload
        budgetedRetryHandler.recordRequest(service);
        assertTrue(budgetedRetryHandler.canRetry(service, budget));
        assertTrue(budgetedRetryHandler.canRetry(service, budget));
        assertTrue(budgetedRetryHandler.canRetry(service, budget));
        assertFalse(budgetedRetryHandler.canRetry(service, budget));
        TimeUnit.MILLISECONDS.sleep(1200); // trigger reset counters
        assertFalse(budgetedRetryHandler.canRetry(service, budget));
        budgetedRetryHandler.recordRequest(service);
        assertTrue(budgetedRetryHandler.canRetry(service, budget));

        final String baidu = "baidu";
        assertFalse(budgetedRetryHandler.canRetry(baidu, budget)); // 因为此时还没有请求
        budgetedRetryHandler.recordRequest(baidu);
        assertTrue(budgetedRetryHandler.canRetry(baidu, budget)); // 因为此时还从来没有retry
        assertFalse(budgetedRetryHandler.canRetry(baidu, budget));
    }

    @Test
    void edge_cases() {
        OverloadHandler handler = new BudgetedRetryHandler(-10);
        handler.recordRequest("foo");
        assertTrue(handler.canRetry("foo", 100));

        assertFalse(handler.canRetry("foo", -9));
        for (int i = 0; i < 10; i++) {
            handler.recordRequest("foo");
            assertFalse(handler.canRetry("foo", -9));
        }
    }
}
