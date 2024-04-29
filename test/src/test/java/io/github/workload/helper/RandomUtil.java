package io.github.workload.helper;

import io.github.workload.WorkloadPriority;

import java.util.concurrent.ThreadLocalRandom;

public class RandomUtil {
    public static WorkloadPriority randomWorkloadPriority() {
        return WorkloadPriority.fromP(randomP());
    }

    public static int randomP() {
        return ThreadLocalRandom.current().nextInt(WorkloadPriority.MAX_P);
    }

    public static boolean randomTrue() {
        return ThreadLocalRandom.current().nextDouble() > 0.5;
    }

    public static boolean randomTrue(int possibilityThousandth) {
        return ThreadLocalRandom.current().nextInt(1000) < possibilityThousandth;
    }
}
