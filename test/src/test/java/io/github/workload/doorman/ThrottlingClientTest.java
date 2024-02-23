package io.github.workload.doorman;

import io.github.workload.BaseConcurrentTest;
import io.github.workload.helper.RandomUtil;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ThrottlingClientTest extends BaseConcurrentTest {

    @Test
    void double_multiply_int() {
        final double k = 1.2;
        log.info("{}", 2 * k, (int) (2 * k));
    }

    @Test
    void badCase() {
        assertThrows(IllegalArgumentException.class, () -> {
            new ThrottlingClient(1);
        });

        new ThrottlingClient(1.1);
        new ThrottlingClient(9);
        new ThrottlingClient(98);
    }

    @Test
    void always_pass_if_backend_always_accepts() {
        ThrottlingClient throttler = new ThrottlingClient(2.5);
        for (int i = 0; i < 1000; i++) {
            assertTrue(throttler.attemptRequest());
            Metric metric = throttler.window.currentBucket().data();
            assertEquals(metric.accepts(), metric.requests());
        }
        assertEquals(1000, throttler.window.currentBucket().data().accepts());
    }

    @Test
    void simulate() {
        ThrottlingClient throttler = new ThrottlingClient(2.5);
        int N = 10 << 10;
        int localPass = 0;
        int backendRejects = 0;
        for (int i = 0; i < N; i++) {
            if (throttler.attemptRequest()) {
                localPass++;

                // 模拟：客户端放行了，但被服务器限流了
                final boolean backendAccepted = RandomUtil.randomTrue(600);
                if (!backendAccepted) {
                    backendRejects++;
                    throttler.backendRejected();
                }
            }
        }

        log.info("total:{}, local pass:{}, local pass but backend rejected:{}",
                N, localPass, backendRejects);
    }

    @RepeatedTest(10)
    void testRandomBoolean() {
        int trueN = 0;
        final int N = 1000;
        for (int i = 0; i < N; i++) {
            if (RandomUtil.randomTrue(200)) {
                trueN++;
            }
        }
        log.info("{} out of {}: {}%", trueN, N, trueN * 100 / N);
    }

}