package io.github.workload.overloading;

import java.util.concurrent.ThreadLocalRandom;

class RandomUtil {
    static WorkloadPriority randomWorkloadPriority() {
        int P = ThreadLocalRandom.current().nextInt(WorkloadPriority.ofLowestPriority().P());
        return WorkloadPriority.fromP(P);
    }

    static boolean randomBoolean() {
        if (ThreadLocalRandom.current().nextDouble() > 0.5) {
            return true;
        }

        return false;
    }

    static boolean randomBoolean(int possibilityThousandth) {
        return ThreadLocalRandom.current().nextInt(1000) <= possibilityThousandth;
    }
}
