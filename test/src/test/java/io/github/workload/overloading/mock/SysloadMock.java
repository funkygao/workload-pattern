package io.github.workload.overloading.mock;

import io.github.workload.Sysload;

import java.util.concurrent.ThreadLocalRandom;

public class SysloadMock implements Sysload {
    private final double base;

    public SysloadMock() {
        this(0.2);
    }

    public SysloadMock(double base) {
        this.base = base;
    }

    @Override
    public double cpuUsage() {
        return base + ThreadLocalRandom.current().nextDouble(0.8d);
    }

}
