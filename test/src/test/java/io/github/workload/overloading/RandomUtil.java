package io.github.workload.overloading;

import java.util.concurrent.ThreadLocalRandom;

public class RandomUtil {
    public static WorkloadPriority randomWorkloadPriority() {
        int P = ThreadLocalRandom.current().nextInt(WorkloadPriority.ofLowestPriority().P());
        return WorkloadPriority.fromP(P);
    }

    public static boolean randomBoolean() {
        if (ThreadLocalRandom.current().nextDouble() > 0.5) {
            return true;
        }

        return false;
    }

    public static boolean randomBoolean(int possibilityThousandth) {
        return ThreadLocalRandom.current().nextInt(1000) <= possibilityThousandth;
    }
}
