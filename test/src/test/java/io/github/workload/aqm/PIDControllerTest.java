package io.github.workload.aqm;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ThreadLocalRandom;

class PIDControllerTest {
    private static final int MAX_REQUESTS = 1000;
    private int allowedRequests = MAX_REQUESTS;
    private double currentUtilization;

    @Test
    void demo() throws InterruptedException {
        // cpu使用率期望值：65%，增益参数使用一个非常保守的起始点
        // 如何调整增益参数？
        // 如果系统对负载变化的响应太慢，可以逐渐增加kp
        // 如果系统在达到目标CPU使用率后有持续的小幅波动，可以逐渐增加ki以消除这种稳态误差
        // 如果在调整过程中出现了过冲或振荡，可以增加kd以抑制这种现象
        // 在调整过程中以小步进行，每次只调整一个参数，并观察改变对系统的影响
        PIDController pid = new PIDController(0.1, 0.01, 0.01, 65);
        for (int i = 0; i < 100; i++) {
            currentUtilization = getCurrentCPUUtilization();
            double controlOutput = pid.compute(currentUtilization);
            // 应用控制器的输出
            adjustWorkload(controlOutput);
        }
    }

    private double getCurrentCPUUtilization() {
        return ThreadLocalRandom.current().nextDouble(100d);
    }

    private void adjustWorkload(double controlOutput) {
        final int wasAllowed = allowedRequests;

        int adjustment = (int) (controlOutput * MAX_REQUESTS);
        allowedRequests += adjustment;

        // 确保允许的请求量不超过最大值也不小于0
        allowedRequests = Math.min(MAX_REQUESTS, allowedRequests);
        allowedRequests = Math.max(0, allowedRequests);

        System.out.printf("%5.2f   %6.2f  允许并发请求：%d -> %d\n", currentUtilization, controlOutput, wasAllowed, allowedRequests);
    }

}