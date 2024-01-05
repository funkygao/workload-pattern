package io.github.workload.overloading;

import io.github.workload.helper.CpuStressLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WorkloadShedderOnCpuTest {

    @AfterEach
    void cleanup() {
        CpuStressLoader.stop();
    }

    @Test
    void basic() {
        WorkloadShedderOnCpu shedder = new WorkloadShedderOnCpu(AdmissionController.CPU_USAGE_UPPER_BOUND, 0);
        assertFalse(shedder.isOverloaded(System.nanoTime(), null));
    }

    @Test
    void forceOverloaded() throws InterruptedException {
        WorkloadShedderOnCpu shedder = new WorkloadShedderOnCpu(AdmissionController.CPU_USAGE_UPPER_BOUND, 1);
        CpuStressLoader.burnCPUs();
        for (int i = 0; i < 10; i++) {
            if (shedder.isOverloaded(0, null)) {
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

}