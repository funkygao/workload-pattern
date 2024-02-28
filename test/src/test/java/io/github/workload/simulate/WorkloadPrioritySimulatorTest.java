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
        int totalRequests = 0;
        for (Map.Entry<WorkloadPriority, Integer> entry : simulator.simulateFullyRandomWorkloadPriority(10)) {
            totalRequests += entry.getValue();

            assertTrue(entry.getValue() < 10);
        }
        assertEquals(WorkloadPriority.MAX_P + 1, simulator.size());
        assertEquals(totalRequests, simulator.totalRequests());

        assertEquals(0, simulator.reset().size());

        assertEquals(10, simulator.reset().simulateRpcWorkloadPriority(10).totalRequests());

        assertEquals(100, simulator.reset().simulateMixedWorkloadPriority(100).totalRequests());
    }

}