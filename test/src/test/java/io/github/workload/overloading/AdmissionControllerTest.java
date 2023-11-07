package io.github.workload.overloading;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdmissionControllerTest {

    @Test
    void basic() {
        AdmissionController controller = new AdmissionController();
        assertTrue(controller.admit(WorkloadPriority.of(4, 6)));
        controller.recordQueuedNs(5 * 1000_000); // 5ms
        controller.recordQueuedNs(10 * 1000_000); // 5ms
        assertEquals(15 * 1000_000, controller.detector().accumulatedWaitNs());
    }
}

