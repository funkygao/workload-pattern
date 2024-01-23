package io.github.workload.overloading;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FqCodelAdmissionControllerTest {

    @Test
    void basic() {
        AdmissionController controller = FqCodelAdmissionController.getInstance("a");
        assertSame(controller, FqCodelAdmissionController.getInstance("a"));
    }

}