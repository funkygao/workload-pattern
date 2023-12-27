package io.github.workload.window.example;

import io.github.workload.AbstractBaseTest;
import io.github.workload.overloading.RandomUtil;
import io.github.workload.overloading.WorkloadPriority;
import io.github.workload.window.*;
import org.junit.jupiter.api.Test;

import java.util.function.BiConsumer;

class TopicFairPartitioner extends AbstractBaseTest {

    @Test
    void demo() {
        BiConsumer<Long, CountWindowState> onWindowSwap = (nowNs, currentWindow) -> {
            // 窗口切换时进行业务逻辑处理：这里为了演示，只是打印日志
            log.info("{} {}", currentWindow.requested(), currentWindow.histogram());
        };
        WindowConfig config = new WindowConfig(0, 2000,
                new CountRolloverStrategy(), onWindowSwap);
        // 创建这个核心的线程安全的滚动窗口
        TumblingWindow window = new TumblingWindow<CountWindowState>(0, "MQ", config);
        for (int i = 0; i < 1 << 20; i++) {
            // 模拟发送一条消息
            WorkloadPriority priority = RandomUtil.randomWorkloadPriority();
            // 采样，并推动窗口前进
            window.advance(priority);
        }
    }
}
