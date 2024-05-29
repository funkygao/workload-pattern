package io.github.workload.overloading.handler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OverloadHandlerTest {

    @Test
    void basic() {
        OverloadHandler handler = OverloadHandler.ofRetryBudget();
        assertTrue(handler instanceof BudgetedRetryHandler);
    }

}