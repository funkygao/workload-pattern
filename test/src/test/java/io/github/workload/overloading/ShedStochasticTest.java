package io.github.workload.overloading;

import io.github.workload.BaseTest;
import io.github.workload.WorkloadPriority;
import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ShedStochasticTest extends BaseTest {

    @Test
    void basic() {
        ShedStochastic stochastic = ShedStochastic.newDefault();
        for (int b : new int[]{WorkloadPriority.B_CRITICAL_PLUS, WorkloadPriority.B_CRITICAL, WorkloadPriority.B_SHEDDABLE_PLUS, WorkloadPriority.B_SHEDDABLE}) {
            WorkloadPriority priority = WorkloadPriority.ofPeriodicRandomFromUID(b, 0);
            final int N = 100;
            int shedded = 0;
            for (int i = 0; i < N; i++) {
                boolean shed = stochastic.shouldShed(priority);
                if (shed) {
                    shedded++;
                }
            }
            log.info("B:{} stochastic:{}/{}", priority.B(), shedded, N);
        }
    }

    @Test
    void linearInterpolation() {
        setLogLevel(Level.TRACE);
        ShedStochastic stochastic = ShedStochastic.newDefault();
        for (int B : new int[]{WorkloadPriority.B_CRITICAL_PLUS - 2, WorkloadPriority.B_CRITICAL - 2, WorkloadPriority.B_SHEDDABLE_PLUS - 2, WorkloadPriority.B_SHEDDABLE - 2, WorkloadPriority.B_SHEDDABLE + 2}) {
            WorkloadPriority priority = WorkloadPriority.ofPeriodicRandomFromUID(B, 1);
            log.info("B:{} {}", priority.B(), stochastic.shouldShed(priority));
        }
        setLogLevel(Level.DEBUG);
    }

    @Test
    @Disabled
    void benchmark() {
        ShedStochastic stochastic = ShedStochastic.newDefault();
        WorkloadPriority priority = WorkloadPriority.ofPeriodicRandomFromUID(WorkloadPriority.B_CRITICAL, 5);
        long t0 = System.nanoTime();
        int N = 1 << 30;
        // 空循环用于基准测试，同时引入不可预测的计算，确保JVM不会将空循环优化掉
        long dummy = 0;
        for (int i = 0; i < N; i++) {
            dummy += i;
        }
        double baseOps = (double) (System.nanoTime() - t0) / N;
        t0 = System.nanoTime();
        for (int i = 0; i < N; i++) {
            stochastic.shouldShed(priority);
        }
        final double ops = (double) (System.nanoTime() - t0) / N - baseOps;
        log.info("{} ops/ns, base:{}", (double) (System.nanoTime() - t0) / N - baseOps, baseOps);
        assertTrue(ops < 10);
    }

}