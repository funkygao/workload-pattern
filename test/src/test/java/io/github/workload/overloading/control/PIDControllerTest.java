package io.github.workload.overloading.control;

import io.github.workload.BaseTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PIDControllerTest extends BaseTest {
    private static final int MAX_REQUESTS = 1000;
    private int allowedRequests = MAX_REQUESTS;
    private double currentUtilization;

    @DisplayName("根据watermark预测与实际的误差，微调watermark")
    @Test
    void overloadWatermark() {
        // setpoint 100%
        PIDController pid = new PIDController(0.5, 0.01, 0.1);
        PIDController pid2 = new PIDController(0.1, 0.01, 0.05);
        List<String> errors = new LinkedList<>();
        List<String> adjustments = new LinkedList<>();
        List<String> adjustments2 = new LinkedList<>();
        for (int i = 0; i < 20; i++) {
            double err = ThreadLocalRandom.current().nextDouble(0.4);
            errors.add(String.format("%.2f", err));
            double adjustment = pid.getOutput(err, System.nanoTime());
            adjustments.add(String.format("%.2f", adjustment));
            adjustment = pid2.getOutput(err, System.nanoTime());
            adjustments2.add(String.format("%.2f", adjustment));
        }
        log.info("err: {}", errors);
        log.info("adj: {}", adjustments);
        log.info("adj: {}", adjustments2);

        pid = new PIDController(0.5, 0.01, 0.1);
        for (int i = 0; i < 100; i++) {
            assertEquals(0, pid.getOutput(0, System.nanoTime()));
        }
    }

    @Test
    void demo() throws InterruptedException {
        // cpu使用率期望值：65%，增益参数使用一个非常保守的起始点
        // 如何调整增益参数？
        // 如果系统对负载变化的响应太慢，可以逐渐增加kp
        // 如果系统在达到目标CPU使用率后有持续的小幅波动，可以逐渐增加ki以消除这种稳态误差
        // 如果在调整过程中出现了过冲或振荡，可以增加kd以抑制这种现象
        // 在调整过程中以小步进行，每次只调整一个参数，并观察改变对系统的影响
        PIDController pid = new PIDController(0.1, 0.01, 0.01);
        for (int i = 0; i < 100; i++) {
            currentUtilization = getCurrentCPUUtilization();
            double pidValue = pid.getOutput(65 - currentUtilization, System.nanoTime());
            log.info("{} target:{}, sample:{}, pid:{}", i, 65, currentUtilization, pidValue);

            // 应用控制器的输出
            adjustWorkload(pidValue);
        }
    }

    private double getCurrentCPUUtilization() {
        return ThreadLocalRandom.current().nextDouble(100d);
    }

    private void adjustWorkload(double PID) {
        final int wasAllowed = allowedRequests;

        int adjustment = (int) (PID * MAX_REQUESTS);
        allowedRequests += adjustment;

        // 确保允许的请求量不超过最大值也不小于0
        allowedRequests = Math.min(MAX_REQUESTS, allowedRequests);
        allowedRequests = Math.max(0, allowedRequests);

        System.out.printf("%5.2f   %6.2f  允许并发请求：%d -> %d\n", currentUtilization, PID, wasAllowed, allowedRequests);
    }

}