package io.github.workload.overloading.mock;

import io.github.workload.Sysload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadLocalRandom;

public class SysloadMock implements Sysload {
    private static final Logger log = LoggerFactory.getLogger(SysloadMock.class);

    private final double base;

    public SysloadMock() {
        this(0.2);
    }

    public SysloadMock(double base) {
        this.base = base;
    }

    @Override
    public double cpuUsage() {
        double usage = base + ThreadLocalRandom.current().nextDouble(0.8d);
        log.debug("cpu usage:{}", usage);
        return usage;
    }
}
