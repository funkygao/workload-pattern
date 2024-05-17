package io.github.workload.simulate;

import io.github.workload.WorkloadPriority;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 全局的业务优先级分配器.
 *
 * @see <a href="https://cloud.google.com/blog/products/gcp/using-load-shedding-to-survive-a-success-disaster-cre-life-lessons">Using load shedding to survive a success disaster</a>
 * @see <a href="https://github.com/apache/hbase/blob/fdde2273006dc3b227d82b297b548885bb9cb48a/hbase-common/src/main/java/org/apache/hadoop/hbase/HConstants.java#L1149">HBase RPC Priority Definition</a>
 */
public interface WorkloadPrioritizer {

    static int randomUid() {
        return ThreadLocalRandom.current().nextInt(Integer.MAX_VALUE);
    }

    // see IP header ToS field for reference
    static WorkloadPriority randomMQ() {
        int b = WorkloadPriority.B_SHEDDABLE_PLUS;
        return WorkloadPriority.ofPeriodicRandomFromUID(b, randomUid());
    }

    static WorkloadPriority randomRpc() {
        int b = WorkloadPriority.B_CRITICAL;
        return WorkloadPriority.ofPeriodicRandomFromUID(b, randomUid());
    }

    static WorkloadPriority randomWeb() {
        int b = WorkloadPriority.B_CRITICAL_PLUS;
        return WorkloadPriority.ofPeriodicRandomFromUID(b, randomUid());
    }

    static WorkloadPriority randomLowPriority() {
        return WorkloadPriority.ofPeriodicRandomFromUID(WorkloadPriority.B_SHEDDABLE, randomUid());
    }

}
