package io.github.workload.overloading.v2;

import io.github.workload.BaseTest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class BreakwaterAdmissionControllerTest extends BaseTest {

    @Test
    @Disabled
    void calculateCredits() {
        BreakwaterAdmissionController controller = new BreakwaterAdmissionController();
        controller.alpha = controller.calculateAlpha(10);
        double credit = 10;
        final double targetDelay = 10;
        double[] actualDelays = new double[]{5, 4, 5, 10, 15, 19, 20, 33, 100, 8, 7, 9};
        double[] expectedCredits = new double[]{10.1};
        for (int i = 0; i < actualDelays.length; i++) {
            final double actualDelay = actualDelays[i];
            final double before = credit;
            credit = controller.calculateCredits(credit, targetDelay, actualDelay);
            //assertEquals(expectedCredits[i], credit, DELTA);
            System.out.printf("%7.2f %7.2f => %-7.2f\n", actualDelay, before, credit);
        }
    }

}