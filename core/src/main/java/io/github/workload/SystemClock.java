package io.github.workload;

import io.github.workload.annotations.ThreadSafe;
import io.github.workload.annotations.VisibleForTesting;
import lombok.Generated;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 支持多精度的系统时钟.
 *
 * <p>多精度，是为了降低获取时间的性能损耗.</p>
 */
@ThreadSafe
@Slf4j
public class SystemClock {
    /**
     * Linux下HZ=100，即1个时间片是10ms，实际的精度大概在(precisionMs+10)范围内.
     */
    public static final int PRECISION_DRIFT_MS = 10;

    private static final String THREAD_NAME = SystemClock.class.getSimpleName();

    private final long precisionMs;
    private final AtomicLong currentTimeMillis = new AtomicLong(System.currentTimeMillis());

    // key is precisionMs
    private static final Map<Long, SystemClock> clocks = new ConcurrentHashMap<>();
    private static Lock clocksLock = new ReentrantLock();

    private static volatile ScheduledFuture<?> timerTask;
    private static volatile long minPrecisionMs = Long.MAX_VALUE;

    @VisibleForTesting
    static final ScheduledExecutorService precisestClockUpdater = Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory(THREAD_NAME));

    private SystemClock(long precisionMs) {
        this.precisionMs = precisionMs;
    }

    /**
     * 获取实时的系统时钟.
     */
    public static SystemClock ofRealtime() {
        return ofPrecisionMs(0);
    }

    /**
     * 获取指定精度(ms)的系统时钟实例.
     *
     * <p>实际的{@link #currentTimeMillis}精度还有考虑到OS调度的精度.</p>
     */
    public static SystemClock ofPrecisionMs(long precisionMs) {
        if (precisionMs < 0) {
            throw new IllegalArgumentException("precisionMs cannot be negative");
        }

        // https://github.com/apache/shardingsphere/pull/13275/files
        // https://bugs.openjdk.org/browse/JDK-8161372
        SystemClock clock = clocks.get(precisionMs);
        if (clock != null) {
            return clock;
        }

        clocksLock.lock();
        try {
            return clocks.computeIfAbsent(precisionMs, key -> {
                log.info("register new clock, precision:{}ms, present clocks:{}", key, clocks.size());
                rescheduleTimerIfNec(key);
                return new SystemClock(key);
            });
        } finally {
            clocksLock.unlock();
        }
    }

    @VisibleForTesting("共享状态清理，以便测试用例隔离")
    @Generated
    static void resetForTesting() {
        if (timerTask != null) {
            timerTask.cancel(true);
            timerTask = null;
        }
        clocks.clear();
        minPrecisionMs = Long.MAX_VALUE;
    }

    /**
     * Returns the current time in milliseconds.
     *
     * <p>根据精度不同，返回的时间可能</p>
     */
    public long currentTimeMillis() {
        return precisionMs == 0 ? System.currentTimeMillis() : currentTimeMillis.get();
    }

    private static void rescheduleTimerIfNec(long precisionMs) {
        if (precisionMs == 0 || precisionMs >= minPrecisionMs) {
            log.info("precision:{}ms need not reschedule timer", precisionMs);
            return;
        }

        clocksLock.lock();
        try {
            // double check: 另外一个线程可能先拿到锁，并修改了 minPrecisionMs
            if (precisionMs >= minPrecisionMs) {
                // minPrecisionMs=100，precisionMs=5 和 precisionMs=10会并发执行
                // 5先拿到锁，minPrecisionMs被改为5；等10拿到锁时，走到这里
                return;
            }

            if (timerTask != null) {
                log.info("reschedule timer: {} -> {}ms", minPrecisionMs, precisionMs);
                timerTask.cancel(true);
            } else {
                log.info("schedule first timer, interval:{}ms", precisionMs);
            }
            minPrecisionMs = precisionMs;
            timerTask = precisestClockUpdater.scheduleAtFixedRate(() -> {
                // scheduleAtFixedRate 会把任务放入queue，即使任务执行时长跨调度周期也不会并发执行
                syncAllClocks();
            }, minPrecisionMs, minPrecisionMs, TimeUnit.MILLISECONDS);
        } finally {
            clocksLock.unlock();
        }
    }

    private static void syncAllClocks() {
        long currentTimeMillis = System.currentTimeMillis();
        // 防止在迭代过程中clocks进行更新：clocks.computeIfAbsent
        clocksLock.lock();
        try {
            for (SystemClock clock : clocks.values()) {
                if (clock.precisionMs != 0) {
                    clock.currentTimeMillis.set(currentTimeMillis);
                }
            }
        } finally {
            clocksLock.unlock();
        }
    }

}
