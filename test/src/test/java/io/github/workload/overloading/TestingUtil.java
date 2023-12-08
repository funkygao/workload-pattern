package io.github.workload.overloading;

import java.util.concurrent.ThreadLocalRandom;

class TestingUtil {
    static WorkloadPriority randomWorkloadPriority() {
        int P = ThreadLocalRandom.current().nextInt(WorkloadPriority.ofLowestPriority().P());
        return WorkloadPriority.fromP(P);
    }
}
