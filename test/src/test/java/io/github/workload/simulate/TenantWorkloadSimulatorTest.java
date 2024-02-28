package io.github.workload.simulate;

import org.junit.jupiter.api.Test;

import java.util.LinkedList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TenantWorkloadSimulatorTest {

    @Test
    void basic() {
        TenantWorkloadSimulator simulator = new TenantWorkloadSimulator();
        List<TenantWeight> weights = new LinkedList<>();
        weights.add(new TenantWeight("foo", 3));
        weights.add(new TenantWeight("bar", 8));
        List<String> expectedTenantWorkloads = new LinkedList<>();
        for (String tenantName : simulator.simulateByWeights(weights)) {
            expectedTenantWorkloads.add(tenantName);
        }
        assertEquals(11, simulator.totalWorkloads());
        assertEquals(expectedTenantWorkloads.size(), simulator.totalWorkloads());
        assertEquals("[foo, bar, foo, bar, foo, bar, bar, bar, bar, bar, bar]", expectedTenantWorkloads.toString());

        assertEquals(0, simulator.reset().totalWorkloads());
    }

}