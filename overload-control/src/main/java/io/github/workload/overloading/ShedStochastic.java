package io.github.workload.overloading;

import io.github.workload.WorkloadPriority;
import lombok.NonNull;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 概率式削减：一种简单且有效解决请求优先级分布不均的方法.
 *
 * <p>对于低优先级请求，不是完全拒绝，而是按一定的概率拒绝.</p>
 * <p>这种方法可以确保每个优先级级别的请求都能得到一定处理，同时又可以限制低优先级请求对系统的影响.</p>
 */
class ShedStochastic {
    private final Map<Integer, Double> shedProbabilities = new HashMap<>();

    static ShedStochastic newDefault() {
        ShedStochastic stochastic = new ShedStochastic();
        stochastic.shedProbabilities.put(WorkloadPriority.B_CRITICAL_PLUS, 0.8);
        stochastic.shedProbabilities.put(WorkloadPriority.B_CRITICAL, 0.85);
        stochastic.shedProbabilities.put(WorkloadPriority.B_SHEDDABLE_PLUS, 0.95);
        stochastic.shedProbabilities.put(WorkloadPriority.B_SHEDDABLE, 1d);
        return stochastic;
    }

    boolean shouldShed(@NonNull WorkloadPriority priority) {
        final double prob = shedProbabilities.getOrDefault(priority.B(), 1d);
        return ThreadLocalRandom.current().nextDouble() < prob;
    }

}
