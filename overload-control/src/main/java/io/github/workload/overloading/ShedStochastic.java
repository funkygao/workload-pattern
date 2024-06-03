package io.github.workload.overloading;

import io.github.workload.WorkloadPriority;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 概率式削减：对已经要削减的工作负荷，进行基于概率的二次确认.
 *
 * <p>一种简单且有效解决请求优先级分布不均的方法</p>
 * <p>对于低优先级请求，不是完全拒绝，而是按一定的概率拒绝.</p>
 * <p>这种方法可以确保每个优先级级别的请求都能得到一定处理，同时又可以限制低优先级请求对系统的影响.</p>
 */
@Slf4j
class ShedStochastic {
    private final TreeMap<Integer, Double> shedProbabilities;

    private ShedStochastic(TreeMap<Integer, Double> shedProbabilities) {
        this.shedProbabilities = shedProbabilities;
    }

    static ShedStochastic newDefault() {
        TreeMap<Integer, Double> probabilities = new TreeMap<>();
        probabilities.put(WorkloadPriority.B_CRITICAL_PLUS, 0.85);
        probabilities.put(WorkloadPriority.B_CRITICAL, 0.91);
        probabilities.put(WorkloadPriority.B_SHEDDABLE_PLUS, 0.95);
        probabilities.put(WorkloadPriority.B_SHEDDABLE, 1d);
        return new ShedStochastic(probabilities);
    }

    boolean shouldShed(@NonNull WorkloadPriority priority) {
        final int B = priority.B();
        Double prob = shedProbabilities.get(B);
        if (prob != null) {
            return ThreadLocalRandom.current().nextDouble() < prob;
        }

        // 查找最近的上下界
        Map.Entry<Integer, Double> lowerEntry = shedProbabilities.floorEntry(B);
        Map.Entry<Integer, Double> higherEntry = shedProbabilities.ceilingEntry(B);
        if (lowerEntry == null) {
            // B < B_CRITICAL_PLUS
            prob = higherEntry.getValue();
        } else if (higherEntry == null) {
            // B > B_SHEDDABLE
            prob = lowerEntry.getValue();
        } else {
            // 线性插值计算概率
            final int lowerKey = lowerEntry.getKey();
            final int higherKey = higherEntry.getKey();
            final double lowerValue = lowerEntry.getValue();
            final double higherValue = higherEntry.getValue();
            prob = lowerValue + (higherValue - lowerValue) * (B - lowerKey) / (double) (higherKey - lowerKey);
        }

        log.trace("B:{} prob:{}", B, prob);
        return ThreadLocalRandom.current().nextDouble() < prob;
    }

}
