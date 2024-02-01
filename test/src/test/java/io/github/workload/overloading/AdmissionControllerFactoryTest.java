package io.github.workload.overloading;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AdmissionControllerFactoryTest {

    @Test
    void basic() {
        AdmissionController controller = AdmissionControllerFactory.getInstance("a",
                () -> new DAGORAdmissionController("a"));
        assertSame(controller, AdmissionControllerFactory.getInstance("a",
                () -> new DAGORAdmissionController("a")));

        AdmissionController controller1 = AdmissionControllerFactory.getInstance("a", () -> new FqCodelAdmissionController("a"));
        assertSame(controller, controller1);
    }

}