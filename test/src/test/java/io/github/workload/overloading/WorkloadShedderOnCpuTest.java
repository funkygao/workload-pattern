package io.github.workload.overloading;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WorkloadShedderOnCpuTest {

    @Test
    void basic() {
        WorkloadShedderOnCpu shedder = new WorkloadShedderOnCpu(AdmissionController.CPU_USAGE_UPPER_BOUND, 0);
        assertFalse(shedder.isOverloaded(System.nanoTime()));
    }

    @Test
    void forceOverloaded() throws InterruptedException {
        WorkloadShedderOnCpu shedder = new WorkloadShedderOnCpu(AdmissionController.CPU_USAGE_UPPER_BOUND, 0);

        CpuStressLoader.burnCPUs();
        for (int i = 0; i < 10; i++) {
            if (shedder.isOverloaded(0)) {
                return;
            }

            // await the CPUs burn
            Thread.sleep(1000);
        }

        fail();
    }

}