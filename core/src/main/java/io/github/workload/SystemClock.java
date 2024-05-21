package io.github.workload;

import io.github.workload.annotations.ThreadSafe;
import io.github.workload.annotations.VisibleForTesting;
import lombok.Generated;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

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
    private static final Map<Long, SystemClock> instances = new ConcurrentHashMap<>();
    private static final AtomicLong minPrecisionMs = new AtomicLong(Long.MAX_VALUE);

    @VisibleForTesting
    private static final ScheduledExecutorService precisestClockUpdater = Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory(THREAD_NAME));

    private static ScheduledFuture<?> timerTask;

    private SystemClock(long precisionMs) {
        this.precisionMs = precisionMs;
    }

    /**
     * 获取实时的系统时钟.
     */
    public static SystemClock ofRealtime(String who) {
        return ofPrecisionMs(0, who);
    }

    /**
     * 获取指定精度(ms)的系统时钟实例.
     *
     * <p>实际的{@link #currentTimeMillis}精度还有考虑到OS调度的精度.</p>
     */
    public static SystemClock ofPrecisionMs(long precisionMs, String who) {
        if (precisionMs < 0) {
            throw new IllegalArgumentException("precisionMs cannot be negative");
        }

        // https://github.com/apache/shardingsphere/pull/13275/files
        // https://bugs.openjdk.org/browse/JDK-8161372
        // 一种推荐的模式，即在使用computeIfAbsent之前先尝试使用get方法，特别是在高并发场景下
        // 这种模式的主要目的是减少computeIfAbsent可能引入的锁竞争
        // computeIfAbsent方法在某些情况下会锁定ConcurrentHashMap的一部分来确保操作的原子性，这可能会导致不必要的性能开销，特别是在键已经存在的情况下
        // 先使用get方法尝试检索键对应的值，只有在键不存在时，再使用computeIfAbsent来添加值，可以避免这种性能开销
        SystemClock clock = instances.get(precisionMs);
        if (clock == null) {
            clock = instances.computeIfAbsent(precisionMs, precision -> {
                if (instances.isEmpty()) {
                    log.info("[{}] register first precision:{}ms timer", who, precision);
                } else {
                    log.info("[{}] register {}nd precision:{}ms timer", who, instances.size() + 1, precision);
                }

                rescheduleTimerIfNec(precision, who);
                return new SystemClock(precision);
            });
        }

        return clock;
    }

    /**
     * Returns the current time in milliseconds.
     *
     * <p>根据精度不同，返回的时间可能</p>
     */
    public long currentTimeMillis() {
        return precisionMs == 0 ? System.currentTimeMillis() : currentTimeMillis.get();
    }

    private static void rescheduleTimerIfNec(long newPrecisionMs, String who) {
        final long currentMinPrecision = minPrecisionMs.get();
        if (newPrecisionMs == 0 || newPrecisionMs >= currentMinPrecision) {
            log.info("[{}] precision:{}ms need not reschedule timer", who, newPrecisionMs);
            return;
        }

        if (minPrecisionMs.compareAndSet(currentMinPrecision, newPrecisionMs)) {
            if (timerTask != null) {
                timerTask.cancel(false);
                log.info("[{}] reschedule timer: {} -> {}ms", who, currentMinPrecision, newPrecisionMs);
            } else {
                log.info("[{}] schedule first timer, interval:{}ms", who, newPrecisionMs);
            }

            // 受CAS保护，确保了timerTask赋值操作的原子性和可见性，因此它没有volatile修饰
            timerTask = precisestClockUpdater.scheduleAtFixedRate(SystemClock::syncAllClocks, newPrecisionMs, newPrecisionMs, TimeUnit.MILLISECONDS);
        }
    }

    private static void syncAllClocks() {
        final long currentTimeMillis = System.currentTimeMillis();
        instances.forEach((precision, clock) -> {
            if (clock.precisionMs != 0) {
                clock.currentTimeMillis.set(currentTimeMillis);
            }
        });
    }

    @VisibleForTesting
    @Generated
    static void resetForTesting() {
        if (timerTask != null) {
            timerTask.cancel(true);
            timerTask = null;
        }
        instances.clear();
        minPrecisionMs.set(Long.MAX_VALUE);
    }

}
