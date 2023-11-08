package io.github.workload.overloading;

import org.junit.jupiter.api.Test;

import java.util.concurrent.RejectedExecutionHandler;

import static org.junit.jupiter.api.Assertions.*;

class AdmissionControllerTest {

    @Test
    void basic() {
        AdmissionController controller = new AdmissionController();
        try {
            controller.admit(null);
            fail();
        } catch (NullPointerException expected) {
        }
        assertTrue(controller.admit(WorkloadPriority.of(4, 6)));
        controller.recordQueuedNs(5 * 1000_000); // 5ms
        controller.recordQueuedNs(10 * 1000_000); // 5ms

        controller.adjustOverloadQueuingMs(100);
        RejectedExecutionHandler handler1 = controller.rejectedExecutionHandler();
        assertNotNull(handler1);
        RejectedExecutionHandler handler2 = controller.rejectedExecutionHandler();
        assertNotSame(handler2, handler1);
    }

    @Test
    void hook() {
        AdmissionController controller = new AdmissionController(20);
        controller.setWindowSlideHook(() -> {
            // 例如，查询队列积压情况，来判断是否已经过载
        });
    }
}

