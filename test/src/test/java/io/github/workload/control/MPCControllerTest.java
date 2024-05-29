package io.github.workload.control;

import io.github.workload.BaseTest;
import org.junit.jupiter.api.Test;

class MPCControllerTest extends BaseTest {
    MPCController controller = new MPCController(4);

    @Test
    void basic() {
        double[] shedRatioErrors = new double[]{0.1, 0.02, 0.5, 0.6, 0.8, 0.4, 1.2};
        for (double err : shedRatioErrors) {
            log.info("err:{}, shouldShed:{}", err, controller.updateModel(err).shouldShed());
        }
    }
}
