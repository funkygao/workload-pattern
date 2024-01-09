package io.github.workload.window.kafka;

import io.github.workload.BaseConcurrentTest;
import io.github.workload.helper.RandomUtil;
import io.github.workload.overloading.WorkloadPriority;
import io.github.workload.window.CountRolloverStrategy;
import io.github.workload.window.CountWindowState;
import io.github.workload.window.TumblingWindow;
import io.github.workload.window.WindowConfig;
import org.junit.jupiter.api.Test;

class TopicFairPartitioner extends BaseConcurrentTest {

    @Test
    void demo() {
        WindowConfig<CountWindowState> config = WindowConfig.create(0, 2000,
                new CountRolloverStrategy() {
                    @Override
                    public void onRollover(long nowNs, CountWindowState currentWindow, TumblingWindow<CountWindowState> window) {
                        // 窗口切换时进行业务逻辑处理：这里为了演示，只是打印日志
                        log.info("{} {} {}", window.getName(), currentWindow.requested(), currentWindow.histogram());
                    }
                });
        // 创建这个核心的线程安全的滚动窗口
        TumblingWindow<CountWindowState> window = new TumblingWindow(config, "MQ", 0);
        for (int i = 0; i < 1 << 20; i++) {
            // 模拟发送一条消息
            WorkloadPriority priority = RandomUtil.randomWorkloadPriority();
            // 采样，并推动窗口前进
            window.advance(priority);
        }
    }
}
