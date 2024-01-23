package io.github.workload.overloading.bufferbloat.aqm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @see <a href="https://github.com/facebook/folly/blob/bd600cd4e88f664f285489c76b6ad835d8367cd2/folly/executors/Codel.cpp">Facebook RPC CoDel</a>
 */
class CoDelFacebook {
    private static final int CODEL_INTERVAL = 100; // Interval time in ms
    private static final int CODEL_TARGET_DELAY = 5; // Target codel queueing delay in ms

    private final Options options;

    // 持续维护这两个值
    private AtomicLong minDelayNs;
    private AtomicLong intervalTimeNs;

    private AtomicBoolean resetDelay;
    private AtomicBoolean overloaded;

    CoDelFacebook(Options options) {
        this.options = options;

        minDelayNs = new AtomicLong(0);
        intervalTimeNs = new AtomicLong(Instant.now().toEpochMilli() * 1_000_000);
        resetDelay = new AtomicBoolean(true);
        overloaded = new AtomicBoolean(false);
    }

    public boolean overloaded(Duration delay) {
        final Instant now = Instant.now();
        if (isNewInterval(now)) {
            if (Duration.ofNanos(minDelayNs.get()).compareTo(options.targetDelay) > 0) {
                overloaded.set(true);
            } else {
                overloaded.set(false);
            }
            resetDelay.set(false); // 明确重置resetDelay标志位
        }

        // 如果是新的时间间隔，重置minDelay
        if (resetDelay.compareAndSet(true, false)) {
            minDelayNs.set(delay.toNanos());
            return false; // 在时间间隔的开始，不应该抛弃任何请求
        }

        // 如果提供的延迟小于当前的最小延迟，则更新最小延迟
        minDelayUpdate(delay);

        return shouldShedRequest(delay);
    }

    private boolean isNewInterval(Instant now) {
        final long intervalEnd = intervalTimeNs.get() / 1_000_000;
        final Instant intervalInstant = Instant.ofEpochMilli(intervalEnd);
        if (now.isAfter(intervalInstant)) {
            intervalTimeNs.set((now.toEpochMilli() + options.interval.toMillis()) * 1_000_000);
            return true;
        }
        return false;
    }

    private void minDelayUpdate(Duration delay) {
        long currentMinDelay = minDelayNs.get();
        long currentDelayNs = delay.toNanos();
        if (currentDelayNs < currentMinDelay) {
            minDelayNs.set(currentDelayNs);
        }
    }

    private boolean shouldShedRequest(Duration delay) {
        Duration sloughTimeout = getSloughTimeout(options.targetDelay);
        // 过载并且请求延迟超过两倍的目标延迟
        return overloaded.get() && delay.compareTo(sloughTimeout) > 0;
    }

    // ask for an instantaneous load estimates
    public int getLoad() {
        // it might be better to use the average delay instead of minDelay, but we'd
        // have to track it. aspiring bootcamper?
        Duration minDelay = Duration.ofNanos(minDelayNs.get());
        return Math.min(100, (int) (100 * minDelay.toNanos() / getSloughTimeout(options.targetDelay).toNanos()));
    }

    // ask for the minimum delay observed during this interval
    public Duration getMinDelay() {
        return Duration.ofNanos(minDelayNs.get());
    }

    private Duration getSloughTimeout(Duration delay) {
        return delay.multipliedBy(2);
    }

    public static void main(String[] args) throws InterruptedException {
        final Logger log = LoggerFactory.getLogger("FbCodel");
        // calculate the time each request spent in the queue
        // and feed that delay to overloaded(), which will tell you whether to shed this request
        CoDelFacebook codel = new CoDelFacebook(new Options(CODEL_INTERVAL, CODEL_TARGET_DELAY));
        for (int i = 0; i < 100; i++) {
            int r = 3 + ThreadLocalRandom.current().nextInt(40);
            Duration queuedMs = Duration.ofMillis(r);
            Thread.sleep(ThreadLocalRandom.current().nextInt(r));
            boolean overloaded= codel.overloaded(queuedMs);
            if (overloaded) {
                log.info("queued:{}ms, overloaded:{}, minDelay:{}, load:{}",
                        queuedMs.toMillis(), overloaded, codel.getMinDelay(), codel.getLoad());
            }
        }
    }

    public static class Options {
        private Duration targetDelay;
        private Duration interval;

        public Options(int intervalMs, int targetDelayMs) {
            this.interval = Duration.ofMillis(intervalMs);
            this.targetDelay = Duration.ofMillis(targetDelayMs);
        }
    }

}
