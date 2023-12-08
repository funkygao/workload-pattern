package io.github.workload.overloading;

import java.util.Random;

class TestingUtil {
    private static final Random rand = new Random();

    static WorkloadPriority randomWorkloadPriority() {
        int P = rand.nextInt(WorkloadPriority.ofLowestPriority().P());
        return WorkloadPriority.fromP(P);
    }
}
