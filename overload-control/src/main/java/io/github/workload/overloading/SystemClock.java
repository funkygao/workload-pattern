package io.github.workload.overloading;

import lombok.AllArgsConstructor;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

class SystemClock {
    private final long precision;
    private final AtomicLong currentTimeMillis;

    SystemClock(long precisionInMs, String threadName) {
        this.precision = precisionInMs <= 0 ? 0 : precisionInMs;
        currentTimeMillis = new AtomicLong(System.currentTimeMillis());
        if (this.precision > 0) {
            if (threadName == null) {
                threadName = "Workload Overloading Clock";
            }
            scheduleClockUpdating(threadName);
        }
    }

    long currentTimeMillis() {
        return precision == 0 ? System.currentTimeMillis() : currentTimeMillis.get();
    }

    private void scheduleClockUpdating(final String threadName) {
        Updater updater = new Updater(currentTimeMillis);
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
                r -> {
                    Thread namedThread = new Thread(r, threadName);
                    namedThread.setDaemon(true);
                    return namedThread;
                });
        scheduler.scheduleAtFixedRate(updater, precision, precision, TimeUnit.MILLISECONDS);
    }

    @AllArgsConstructor
    private static class Updater implements Runnable {
        private final AtomicLong currentTimeMillis;
        public void run() {
            currentTimeMillis.set(System.currentTimeMillis());
        }
    }
}
