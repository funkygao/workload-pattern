package io.github.workload.overloading;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class SafeSystemClock {
    private static final String ThreadName = SafeSystemClock.class.getSimpleName();

    private final long precision;
    private final AtomicLong currentTimeMillis;
    private static final Map<Long, SafeSystemClock> INSTANCE_MAP = new ConcurrentHashMap<>();
    private static long minPrecision = Integer.MAX_VALUE;
    private static ScheduledExecutorService scheduler;

    private static synchronized void scheduleAtFixedRate(long precision) {
        if (precision > minPrecision || precision == 0) {
            return;
        }

        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        minPrecision = Math.min(minPrecision, precision);
        scheduler = Executors.newSingleThreadScheduledExecutor(
                r -> {
                    Thread namedThread = new Thread(r, ThreadName);
                    namedThread.setDaemon(true);
                    return namedThread;
                });

        scheduler.scheduleAtFixedRate(() -> {
            long currentTimeMillis = System.currentTimeMillis();
            for (SafeSystemClock clock : INSTANCE_MAP.values()) {
                clock.currentTimeMillis.set(currentTimeMillis);
            }
        }, precision, precision, TimeUnit.MILLISECONDS);
    }

    private SafeSystemClock(long precision) {
        this.precision = precision;
        this.currentTimeMillis = new AtomicLong(System.currentTimeMillis());
    }

    public static SafeSystemClock getInstance(long precision) {
        if (INSTANCE_MAP.containsKey(precision)) {
            return INSTANCE_MAP.get(precision);
        } else {
            SafeSystemClock newInstance = new SafeSystemClock(precision);
            SafeSystemClock oldInstance = INSTANCE_MAP.putIfAbsent(precision, newInstance);
            if (oldInstance == null) {
                scheduleAtFixedRate(precision);
                return newInstance;
            } else {
                return oldInstance;
            }
        }
    }

    public long currentTimeMillis() {
        return precision == 0 ? System.currentTimeMillis() : currentTimeMillis.get();
    }
}
