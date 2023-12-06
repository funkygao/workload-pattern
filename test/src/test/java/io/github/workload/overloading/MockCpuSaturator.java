package io.github.workload.overloading;

import java.util.Random;

class MockCpuSaturator implements CpuSaturator {
    private Random random;

    @Override
    public boolean saturated() {
        if (random.nextInt(1) == 1) {
            return false;
        }
        return true;
    }
}
