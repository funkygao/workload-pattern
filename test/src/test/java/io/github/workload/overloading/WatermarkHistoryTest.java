package io.github.workload.overloading;

import io.github.workload.BaseTest;
import io.github.workload.WorkloadPriority;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WatermarkHistoryTest extends BaseTest {
    WatermarkHistory history = new WatermarkHistory(4);

    @Test
    void basic() {
        for (int i = 0; i < 1 << 20; i++) {
            double[] shedRatios = new double[]{0.1, 0.15, 0.08, 0, 0, 0.01, 0.21, 0.3, 0.31, 0.23};
            int[] watermarkPs = new int[]{4590, 3456, 8988, 8763, 9999, 345, 2345, 5698, 4521, 321};
            for (int j = 0; j < shedRatios.length; j++) {
                WorkloadPriority watermark = WorkloadPriority.fromP(watermarkPs[j]);
                history.addHistory(shedRatios[j], watermark);
            }
            assertEquals(2345, history.lastWatermark().P());
        }
    }
}
