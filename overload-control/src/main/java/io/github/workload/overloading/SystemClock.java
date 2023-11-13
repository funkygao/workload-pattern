package io.github.workload.overloading;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

class SystemClock {
    private static final String ThreadName = SystemClock.class.getSimpleName();

    private final long precisionMs;
    private static long minPrecisionMs = Long.MAX_VALUE;
    private final AtomicLong currentTimeMillis;
    private static final Map<Long, SystemClock> INSTANCE_MAP = new ConcurrentHashMap<>();
    private static ScheduledExecutorService scheduler;

    private static synchronized void scheduleAtFixedRate(long precisionMs) {
        if (precisionMs > minPrecisionMs || precisionMs == 0) {
            return;
        }

        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        minPrecisionMs = Math.min(minPrecisionMs, precisionMs);
        scheduler = Executors.newSingleThreadScheduledExecutor(
                r -> {
                    Thread namedThread = new Thread(r, ThreadName);
                    namedThread.setDaemon(true);
                    return namedThread;
                });

        scheduler.scheduleAtFixedRate(() -> {
            long currentTimeMillis = System.currentTimeMillis();
            for (SystemClock clock : INSTANCE_MAP.values()) {
                clock.currentTimeMillis.set(currentTimeMillis);
            }
        }, precisionMs, precisionMs, TimeUnit.MILLISECONDS);
    }

    private SystemClock(long precisionMs) {
        this.precisionMs = precisionMs;
        this.currentTimeMillis = new AtomicLong(System.currentTimeMillis());
    }

    public static SystemClock getInstance(long precisionMs) {
        if (INSTANCE_MAP.containsKey(precisionMs)) {
            return INSTANCE_MAP.get(precisionMs);
        } else {
            SystemClock newInstance = new SystemClock(precisionMs);
            SystemClock oldInstance = INSTANCE_MAP.putIfAbsent(precisionMs, newInstance);
            if (oldInstance == null) {
                scheduleAtFixedRate(precisionMs);
                return newInstance;
            } else {
                return oldInstance;
            }
        }
    }

    public long currentTimeMillis() {
        return precisionMs == 0 ? System.currentTimeMillis() : currentTimeMillis.get();
    }
}
