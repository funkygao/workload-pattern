package io.github.workload.metrics.sliding;

import io.github.workload.BaseTest;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.concurrent.ThreadLocalRandom;

class UrgentPartitionerTest extends BaseTest {

    private final AdaptiveWindow window = new AdaptiveWindow(5, 60 * 1000);

    @RepeatedTest(20)
    @Execution(ExecutionMode.CONCURRENT)
    void demo() {
        AdaptiveWindow.Counter counter = window.currentBucket().data();
        batchSendMessages(counter);
        log.info("{}/{}, urgent messages percent:{}", counter.totalMessages, counter.urgentMessages, counter.urgentPercent());
        long total = 0;
        long urgent = 0;
        for (AdaptiveWindow.Counter c : window.values()) {
            total += c.totalMessages.longValue();
            urgent += c.urgentMessages.longValue();
        }
        log.info("{}, {}", total, urgent);
    }

    private void batchSendMessages(AdaptiveWindow.Counter counter) {
        for (int i = 0; i < 1 << 20; i++) {
            boolean urgent = ThreadLocalRandom.current().nextInt(100) > 85;
            counter.sendMessage(urgent);
        }
    }

}
