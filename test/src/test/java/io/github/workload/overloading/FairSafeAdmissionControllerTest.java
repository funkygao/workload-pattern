package io.github.workload.overloading;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FairSafeAdmissionControllerTest {

    @Test
    void basic() {
        AdmissionController controller = AdmissionController.getInstance("foo");
        try {
            controller.admit(null);
            fail();
        } catch (NullPointerException expected) {
        }
        assertTrue(controller.admit(WorkloadPriority.of(4, 6)));
        controller.recordQueuedNs(5 * 1000_000); // 5ms
        controller.recordQueuedNs(10 * 1000_000); // 5ms
    }

    @Test
    void markOverloaded() throws InterruptedException {
        FairSafeAdmissionController controller = (FairSafeAdmissionController) AdmissionController.getInstance("foo");
        WorkloadShedder detector = controller.workloadShedderOnQueue;
        controller.overloaded();
        assertTrue(detector.isOverloaded(System.nanoTime()));
        Thread.sleep(1200); // 经过一个时间窗口
        assertFalse(detector.isOverloaded(System.nanoTime()));
    }

}

