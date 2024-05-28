package io.github.workload.overloading;

import io.github.workload.BaseTest;
import io.github.workload.WorkloadPriority;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ShedStochasticTest extends BaseTest {

    @Test
    void basic() {
        ShedStochastic stochastic = ShedStochastic.newDefault();
        for (int b : new int[]{WorkloadPriority.B_CRITICAL_PLUS, WorkloadPriority.B_CRITICAL, WorkloadPriority.B_SHEDDABLE_PLUS, WorkloadPriority.B_SHEDDABLE}) {
            WorkloadPriority priority = WorkloadPriority.ofPeriodicRandomFromUID(b, 0);
            final int N = 100;
            int shedded = 0;
            for (int i = 0; i < N; i++) {
                boolean shed = stochastic.shouldShed(priority);
                if (shed) {
                    shedded++;
                }
            }
            log.info("B:{} stochastic:{}/{}", priority.B(), shedded, N);
        }

        WorkloadPriority priority = WorkloadPriority.ofPeriodicRandomFromUID(8, 3);
        for (int i = 0; i < 10; i++) {
            assertTrue(stochastic.shouldShed(priority));
        }
    }

}