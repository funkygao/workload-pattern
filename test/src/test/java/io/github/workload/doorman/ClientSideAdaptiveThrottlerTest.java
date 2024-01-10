package io.github.workload.doorman;

import io.github.workload.BaseConcurrentTest;
import io.github.workload.helper.RandomUtil;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClientSideAdaptiveThrottlerTest extends BaseConcurrentTest {

    @Test
    void double_multiply_int() {
        final double k = 1.2;
        log.info("{}", 2 * k, (int) (2 * k));
    }

    @Test
    void badCase() {
        try {
            new ClientSideAdaptiveThrottler(1);
            fail();
        } catch (IllegalArgumentException expected) {
        }

        new ClientSideAdaptiveThrottler(1.1);
        new ClientSideAdaptiveThrottler(9);
        new ClientSideAdaptiveThrottler(98);
    }

    @Test
    void always_pass_if_backend_always_accepts() {
        ClientSideAdaptiveThrottler throttler = new ClientSideAdaptiveThrottler(2.5);
        for (int i = 0; i < 1000; i++) {
            assertTrue(throttler.attemptRequest());
            ClientRequestMetric metric = throttler.window.currentBucket().data();
            assertEquals(metric.accepts(), metric.requests());
        }
        assertEquals(1000, throttler.window.currentBucket().data().accepts());
    }

    @Test
    void simulate() {
        ClientSideAdaptiveThrottler throttler = new ClientSideAdaptiveThrottler(1.5);
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