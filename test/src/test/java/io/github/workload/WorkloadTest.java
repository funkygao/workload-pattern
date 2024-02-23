package io.github.workload;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WorkloadTest {

    @Test
    void basic() {
        Workload workload = Workload.ofPriority(WorkloadPriority.fromP(500));
        assertEquals(0, workload.getCost());
        assertEquals(0, workload.getRetryAttempted());
        assertEquals(10, workload.withCost(10).getCost());
        // cost can be updated multiple times
        assertEquals(11, workload.withCost(11).getCost());
        assertEquals(2, workload.withRetryAttempted(2).getRetryAttempted());
        // retryAttempted can be updated multiple times
        assertEquals(3, workload.withRetryAttempted(3).getRetryAttempted());
    }

    @Test
    void badCases() {
        Workload workload = Workload.ofPriority(WorkloadPriority.fromP(300));
        assertThrows(IllegalArgumentException.class, () -> {
            workload.withCost(-1);
        });
        assertThrows(IllegalArgumentException.class, () -> {
            workload.withRetryAttempted(-1);
        });
    }

}