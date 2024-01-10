package io.github.workload.metrics.tumbling.flexibility;

import io.github.workload.helper.RandomUtil;
import io.github.workload.metrics.tumbling.TumblingWindow;
import io.github.workload.metrics.tumbling.WindowConfig;
import org.junit.jupiter.api.Test;

class WindowFlexibilityDemo {

    // 如何扩充现有的窗口？1.定义一个新的WindowState类 2.实现一个新的滚动策略
    @Test
    void demo() {
        WindowConfig<FooWindowState> config = WindowConfig.create(new FooRolloverStrategy() {
            @Override
            public void onRollover(long nowNs, FooWindowState snapshot, TumblingWindow<FooWindowState> window) {
                System.out.println(snapshot.requested());
            }
        });
        TumblingWindow<FooWindowState> window = new TumblingWindow<>(config, "test", 0);
        for (int i = 0; i < 1000; i++) {
            window.advance(RandomUtil.randomWorkloadPriority());
        }

    }
}
