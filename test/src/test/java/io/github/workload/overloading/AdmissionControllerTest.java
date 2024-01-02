package io.github.workload.overloading;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import static org.junit.jupiter.api.Assertions.*;

class AdmissionControllerTest {

    @RepeatedTest(10)
    @Execution(ExecutionMode.CONCURRENT)
    void getInstance() {
        AdmissionController controller = AdmissionController.getInstance("foo");
        assertTrue(controller instanceof FairSafeAdmissionController);
        assertSame(controller, AdmissionController.getInstance("foo"));
        assertNotSame(controller, AdmissionController.getInstance("bar"));

        try {
            AdmissionController.getInstance(null);
            fail();
        } catch (NullPointerException ok) {

        }
    }

    @Test
    void nonNull() {
        try {
            AdmissionController.getInstance(null);
            fail();
        } catch (NullPointerException expected) {
        }

        AdmissionController controller = AdmissionController.getInstance("test");
        try {
            controller.admit(null);
            fail();
        } catch (NullPointerException expected) {
        }

        try {
            controller.feedback(null);
            fail();
        } catch (NullPointerException expected) {
        }
    }
}
