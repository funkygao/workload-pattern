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

    @Test
    void markOverloaded() throws InterruptedException {
        AdmissionController controller = new AdmissionController();
        OverloadDetector detector = controller.overloadDetector;
        controller.markOverloaded();
        assertTrue(detector.isOverloaded(System.nanoTime()));
        Thread.sleep(1200); // 经过一个时间窗口
        assertFalse(detector.isOverloaded(System.nanoTime()));
    }

    @Test
    void essencePattern() {
        AdmissionController.Essence essence = new AdmissionController.Essence();
        essence.setDropRate(0.02);
        AdmissionController controller = essence.createAdmissionController();
        assertEquals(0.02, controller.overloadDetector.dropRate);
    }
}

