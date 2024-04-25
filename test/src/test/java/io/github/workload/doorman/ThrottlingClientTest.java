package io.github.workload.doorman;

import io.github.workload.BaseConcurrentTest;
import io.github.workload.helper.RandomUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ThrottlingClientTest extends BaseConcurrentTest {

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
            assertTrue(throttler.requestAllows());
            Metric metric = throttler.window.currentBucket().data();
            assertEquals(metric.accepts(), metric.requests());
        }
        assertEquals(1000, throttler.window.currentBucket().data().accepts());
    }

    @Test
    void rejectionProbability() {
        Object[][] fixtures = new Object[][]{
                {1.2, 100, 56, 0.3247524752475247},
                {1.1, 156, 11, 0.9165605095541401},
                {10.0, 100, 100, 0.0}, // 全部请求都接受，则0拒绝率
                {3.0, 100, 50, 0.0},
                {1.1, 100, 50, 0.4455445544554455},
                {1.2, 100, 50, 0.39603960396039606},
                {1.3, 100, 50, 0.3465346534653465},
                {1.4, 100, 50, 0.297029702970297},
                {1.6, 100, 50, 0.19801980198019803},
                {1.7, 100, 50, 0.1485148514851485}, // 相同的拒绝比，K越大
                {1.8, 100, 50, 0.09900990099009901},
                {1.9, 100, 50, 0.04950495049504951},
                {2.0, 100, 50, 0.0},

                {2.0, 100, 10, 0.7920792079207921},
                {2.0, 100, 20, 0.594059405940594},
                {2.0, 100, 30, 0.39603960396039606},
                {2.0, 100, 40, 0.19801980198019803},
                {2.0, 100, 50, 0.0},
                {2.0, 100, 60, 0.0},
                {2.0, 100, 70, 0.0},

                {1.000001, 100, 10, 0.89108900990099},
                {1.001, 100, 10, 0.8909900990099011},
                {1.01, 100, 10,  0.8900990099009901},
                {1.01, 100, 20, 0.79009900990099},
                {1.01, 100, 30, 0.6900990099009902},
                {1.01, 100, 40, 0.5900990099009901},
        };
        ThrottlingClient client = new ThrottlingClient(2.5);
        for (Object[] fixture : fixtures) {
            double K = (double) fixture[0];
            int requests = (int) fixture[1];
            int accepts = (int) fixture[2];
            double expected = (double) fixture[3];
            double actual = client.rejectionProbability(K, requests, accepts);
            assertEquals(expected, actual, DELTA);
        }
    }

    @Test
    void simulate() {
        ThrottlingClient throttler = new ThrottlingClient(2.5);
        int N = 10 << 10;
        int localPass = 0;
        int backendRejects = 0;
        for (int i = 0; i < N; i++) {
            if (throttler.requestAllows()) {
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

}