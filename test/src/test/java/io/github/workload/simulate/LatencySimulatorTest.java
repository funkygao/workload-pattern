package io.github.workload.simulate;

import io.github.workload.BaseTest;
import org.junit.jupiter.api.Test;

class LatencySimulatorTest extends BaseTest {
    
    @Test
    void basic() {
        LatencySimulator simulator = new LatencySimulator(30, 500);
        for (double steepness : new double[]{0.1, 0.2, 0.5, 0.8, 2d, 3d}) {
            for (Integer latency : simulator.simulate(10, steepness)) {
                log.info("steepness:{} latency:{}", steepness, latency);
            }
        }
    }
}
