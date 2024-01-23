package io.github.workload.overloading;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AdmissionControllerFactoryTest {

    @Test
    void basic() {
        AdmissionController controller = AdmissionControllerFactory.getInstance("a",
                () -> new FairSafeAdmissionController("a"));
        assertSame(controller, AdmissionControllerFactory.getInstance("a",
                () -> new FairSafeAdmissionController("a")));

        AdmissionController controller1 = AdmissionControllerFactory.getInstance("a", () -> new FqCodelAdmissionController("a"));
        assertSame(controller, controller1);
    }

}