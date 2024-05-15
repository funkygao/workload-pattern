package io.github.workload.overloading;

import io.github.workload.BaseConcurrentTest;
import io.github.workload.helper.CpuStressLoader;
import io.github.workload.metrics.smoother.ValueSmoother;
import io.github.workload.overloading.mock.SysloadAdaptive;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FairShedderCpuTest extends BaseConcurrentTest {

    @AfterEach
    void cleanup() {
        CpuStressLoader.stop();
    }

    @Test
    void basic() {
        FairShedderCpu shedder = new FairShedderCpu(FairSafeAdmissionController.CPU_USAGE_UPPER_BOUND, 0);
        assertFalse(shedder.isOverloaded(shedder.overloadGradient(System.nanoTime(), null)));
    }

    @Test
    void forceOverloaded() throws InterruptedException {
        FairShedderCpu shedder = new FairShedderCpu(FairSafeAdmissionController.CPU_USAGE_UPPER_BOUND, 1);
        CpuStressLoader.burnCPUs();
        for (int i = 0; i < 10; i++) {
            if (shedder.isOverloaded(shedder.overloadGradient(0, null))) {
                // cool down CPU so that other test cases can work as expected
                CpuStressLoader.stop();
                Thread.sleep(2000);
                return;
            }

            // await the CPUs burn
            Thread.sleep(1000);
        }

        // 没有发现过载?
        fail();
    }

    @Test
    void smoothness() {
        ValueSmoother smoother06 = ValueSmoother.ofEMA(0.6);
        ValueSmoother smoother04 = ValueSmoother.ofEMA(0.4);
        ValueSmoother smoother02 = ValueSmoother.ofEMA(0.2);
        double[] cpu =        new double[]{0.04, 0.08,  0.14,  0.2,   0.18,  0.38,  0.26,  1.0,   0.8,   0.18,  0.21};
        double[] expected06 = new double[]{0.04, 0.064, 0.109, 0.163, 0.173, 0.297, 0.274, 0.709, 0.763, 0.413, 0.291};
        double[] expected04 = new double[]{0.04, 0.056, 0.089, 0.133, 0.152, 0.243, 0.25,  0.55,  0.65,  0.462, 0.361};
        double[] expected02 = new double[]{0.04, 0.048, 0.066, 0.093, 0.11,  0.164, 0.183, 0.346, 0.437, 0.385, 0.35};
        for (int i = 0; i < cpu.length; i++) {
            double v06 = smoother06.update(cpu[i]).smoothedValue();
            assertEquals(expected06[i], v06, 0.002);
            double v04 = smoother04.update(cpu[i]).smoothedValue();
            assertEquals(expected04[i], v04, 0.002);
            double v02 = smoother02.update(cpu[i]).smoothedValue();
            assertEquals(expected02[i], v02, 0.002);
        }
    }

    @Test
    void gradient() {
        FairShedderCpu shedder = new FairShedderCpu(FairSafeAdmissionController.CPU_USAGE_UPPER_BOUND, new SysloadAdaptive());
        double[] cpuUsages = new double[]{0.3, 0.6, 0.89, 0.2, 0.7, 1.0, 0.33};
        boolean[] overloads = new boolean[]{false, false, true, false, true, true, false};
        final double upperBound = 0.67;
        for (int i = 0; i < cpuUsages.length; i++) {
            final double gradient = shedder.gradient(cpuUsages[i], upperBound);
            log.debug("gradient:{}, {}/{}", gradient, cpuUsages[i], upperBound);
            assertEquals(overloads[i], shedder.isOverloaded(gradient));
        }
    }

    @Test
    void gradient_simulation() {
        FairShedderCpu shedder = new FairShedderCpu(FairSafeAdmissionController.CPU_USAGE_UPPER_BOUND, new SysloadAdaptive());
        for (int i = 0; i < 100; i++) {
            shedder.overloadGradient(0, null);
        }
    }

}