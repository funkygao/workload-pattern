package io.github.workload.aqm;

import io.github.workload.BaseConcurrentTest;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ThreadLocalRandom;

class AIMDTest extends BaseConcurrentTest {

    @Test
    void demo() {
        double initialWindowSize = 1.0;    // 初始窗口大小
        double additiveIncrease = 1.0;     // 加性增长因子
        double multiplicativeDecrease = 0.5; // 乘性减少因子
        AIMD aimd = new AIMD(initialWindowSize, additiveIncrease, multiplicativeDecrease);
        for (int i = 0; i < 100; i++) {
            // 假设有10%的几率发生拥塞
            final boolean packetDropped = ThreadLocalRandom.current().nextDouble() > 0.9;
            if (packetDropped) {
                aimd.multiplicativeDecrease();
            } else {
                aimd.additiveIncrease();
            }

            log.info("dropped:{}, window: {}", packetDropped, aimd.windowSize());
        }
    }

}