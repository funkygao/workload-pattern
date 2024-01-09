package io.github.workload.window.flexibility;

import io.github.workload.helper.RandomUtil;
import io.github.workload.window.TumblingWindow;
import io.github.workload.window.WindowConfig;
import org.junit.jupiter.api.Test;

class WindowFlexibilityDemo {

    // 如何扩充现有的窗口？1.定义一个新的WindowState类 2.实现一个新的滚动策略
    @Test
    void demo() {
        WindowConfig<FooWindowState> config = WindowConfig.create(new FooRolloverStrategy() {
            @Override
            public void onRollover(long nowNs, FooWindowState state, TumblingWindow<FooWindowState> window) {
                System.out.println(state.requested());
            }
        });
        TumblingWindow<FooWindowState> window = new TumblingWindow<>(config, "test", 0);
        for (int i = 0; i < 1000; i++) {
            window.advance(RandomUtil.randomWorkloadPriority());
        }

    }
}
