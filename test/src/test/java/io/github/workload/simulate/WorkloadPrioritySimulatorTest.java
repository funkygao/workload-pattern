package io.github.workload.simulate;

import io.github.workload.WorkloadPriority;
import org.junit.jupiter.api.RepeatedTest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkloadPrioritySimulatorTest {

    @RepeatedTest(1)
    void basic() {
        WorkloadPrioritySimulator simulator = new WorkloadPrioritySimulator();
        simulator.simulateFullyRandomWorkloadPriority(10);
        assertEquals(WorkloadPriority.MAX_P + 1, simulator.size());
        for (Map.Entry<WorkloadPriority, Integer> entry : simulator) {
            assertTrue(entry.getValue() < 10);
        }

        int totalRequests = 0;
        for (Map.Entry<WorkloadPriority, Integer> entry : simulator) {
            totalRequests += entry.getValue();
        }
        assertEquals(totalRequests, simulator.totalRequests());
        assertEquals(0, simulator.reset().size());

        simulator.simulateRpcWorkloadPriority(10);
        assertEquals(10, simulator.totalRequests());

        simulator.reset().simulateMixedWorkloadPriority(100);
        assertEquals(100, simulator.totalRequests());
    }

}