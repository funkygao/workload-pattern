package io.github.workload.window.example;

import io.github.workload.AbstractBaseTest;
import io.github.workload.overloading.RandomUtil;
import io.github.workload.overloading.WorkloadPriority;
import io.github.workload.window.*;
import org.junit.jupiter.api.Test;

import java.util.function.BiConsumer;
import java.util.function.Function;

class TumblingWindowExample extends AbstractBaseTest {

    @Test
    void forMessageQueue() {
        WindowRolloverStrategy strategy = new CountRolloverStrategy();
        BiConsumer<Long, CountWindowState> onWindowSwap = (nowNs, state) -> {
            log.info("{} {}", state.requested(), state.histogram());
        };
        WindowConfig config = new WindowConfig(0, 2000, strategy, onWindowSwap);
        TumblingWindow window = new TumblingWindow<CountWindowState>(0, "MQ", config);
        for (int i = 0; i < 1 << 20; i++) {
            window.advance(RandomUtil.randomWorkloadPriority());
        }
    }

    void x() {
        Function<WorkloadPriority, Integer> a;
        a = workloadPriority -> workloadPriority.B();
    }
}
