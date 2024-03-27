package io.github.workload.metrics.sliding;

import io.github.workload.BaseConcurrentTest;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.concurrent.ThreadLocalRandom;

class UrgentPartitionerTest extends BaseConcurrentTest {

    private final AdaptiveWindow window = new AdaptiveWindow(5, 60 * 1000);

    @RepeatedTest(20)
    @Execution(ExecutionMode.CONCURRENT)
    void demo() {
        AdaptiveWindow.Counter counter = window.currentBucket().data();
        batchSendMessages(counter);
        log.info("{}/{}, urgent messages percent:{}", counter.totalMessages, counter.urgentMessages, counter.urgentPercent());
    }

    private void batchSendMessages(AdaptiveWindow.Counter counter) {
        for (int i = 0; i < 1 << 20; i++) {
            boolean urgent = ThreadLocalRandom.current().nextInt(100) > 85;
            counter.sendMessage(urgent);
        }
    }

}
