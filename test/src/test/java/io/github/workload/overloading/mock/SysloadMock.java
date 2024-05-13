package io.github.workload.overloading.mock;

import io.github.workload.Sysload;

import java.util.concurrent.ThreadLocalRandom;

public class SysloadMock implements Sysload {
    @Override
    public double cpuUsage() {
        return 0.2 + ThreadLocalRandom.current().nextDouble(0.8d);
    }
}
