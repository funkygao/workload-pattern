package io.github.workload;

public class WorkloadPriorityHelper {

    public static WorkloadPriority of(int b, int u) {
        return WorkloadPriority.of(b, u);
    }

    public static WorkloadPriority ofPeriodicRandomFromUID(int b, int uid, long timeWindowMs) {
        return WorkloadPriority.ofPeriodicRandomFromUID(b, uid, timeWindowMs);
    }
}
