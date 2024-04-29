package io.github.workload.overloading.v2;

import io.github.workload.Workload;
import io.github.workload.WorkloadPriority;
import io.github.workload.annotations.PoC;
import io.github.workload.annotations.Heuristics;
import io.github.workload.metrics.smoother.ValueSmoother;
import io.github.workload.overloading.AdmissionController;
import io.github.workload.overloading.WorkloadFeedback;
import lombok.Generated;
import lombok.NonNull;

import java.util.List;
import java.util.Map;

/**
 * 基于token bucket和class differentiation的准入控制器.
 *
 * @see <a href="https://www.usenix.org/legacy/publications/library/proceedings/usits03/tech/full_papers/welsh/welsh_html/usits.html">Adaptive Overload Control for Busy Internet Servers</a>
 */
@PoC
@Generated
class FairTokenBucketAdmissionController implements AdmissionController {
    private Map<WorkloadPriority, TokenBucket> tokenBuckets;

    @Heuristics
    private final double rateMin = 0.05;
    @Heuristics
    private final double rateMax = 5000;
    @Heuristics
    private final double lowPriorityMultiplicativeDecreaseFactor = 10;

    @Override
    public boolean admit(@NonNull Workload workload) {
        TokenBucket tokenBucket = tokenBuckets.get(workload.getPriority());
        return tokenBucket.tryAcquire();
    }

    @Override
    public void feedback(@NonNull WorkloadFeedback feedback) {
        WorkloadFeedback.Queued queued = (WorkloadFeedback.Queued) feedback;
        WorkloadPriority priority = WorkloadPriority.fromP(5); // TODO feedback里缺少priority信息
        TokenBucket tokenBucket = tokenBuckets.get(priority);
        int action = tokenBucket.update(queued.getQueuedNs());
        // aggressively reduces the rate of lower-priority classes before that of higher-priority classes
        if (action == 1) { // decrease
            for (TokenBucket lowerBucket : bucketsOfLowerPriority(priority)) {
                lowerBucket.decrease(lowPriorityMultiplicativeDecreaseFactor);
            }
        } else if (action == 2) { // increase
            // whenever a higher-priority class exceeds its response time target, all
            // lower-priority classes are flagged to prevent their admission rates from
            // being increased during the next iteration of the controller
        }
    }

    private List<TokenBucket> bucketsOfLowerPriority(WorkloadPriority priority) {
        // 低于该优先级的所有token buckets
        return null;
    }

    @Generated
    private static class TokenBucket {
        private static final ValueSmoother smoother = ValueSmoother.ofSA(0.7);

        // These parameters have been observed to work well across a range of applications
        // however, there are no guarantees that they are optimal
        @Heuristics
        private final double errorToTriggerDecrease = 0.0;
        @Heuristics
        private final double errorToTriggerIncrease = -0.5;
        @Heuristics
        private final double additiveIncreaseFactor = 2.0;
        @Heuristics
        private final double multiplicativeDecreaseFactor = 1.2;

        @Heuristics
        private double targetRt;

        private double rtTP90; // 90th-percentile response time

        private volatile double rate; // adaptive adjust the rate

        // rt: 某个请求的response time
        int update(double rt) {
            rtTP90 = smoother.update(rt).smoothedValue();
            double err = (rtTP90 - targetRt) / targetRt;
            // AIMD
            if (err > errorToTriggerDecrease) {
                decrease(multiplicativeDecreaseFactor);
                return 1;
            } else if (err < errorToTriggerIncrease) {
                increment(additiveIncreaseFactor);
                return 2;
            }
            return 0;
        }

        void decrease(double factor) {
            rate = rate / factor;
        }

        void increment(double factor) {
            rate = rate + factor;
        }

        boolean tryAcquire() {
            // 根据rate计算是否有可发放的token
            return true;
        }
    }
}
