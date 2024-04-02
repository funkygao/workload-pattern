package io.github.workload.aqm;

import io.github.workload.annotations.PoC;

/**
 * Proportion Integration Differentiation，比例积分微分控制.
 *
 * <p>PID控制器通过计算设定点和过程变量（测量值）之间的即偏差，并对其应用比例（P）、积分（I）和微分（D）运算，来产生一个控制偏差的修正信号.</p>
 */
@PoC
class PIDController {
    private final double kp; // 比例增益
    private final double ki; // 积分增益
    private final double kd; // 微分增益

    private final double setpoint; // 设定点，即期望值

    private double lastError; // 上一次的偏差
    private double integral; // 积分项累计

    public PIDController(double kp, double ki, double kd, double setpoint) {
        // Ziegler-Nichols方法调整增益参数
        this.kp = kp;
        this.ki = ki;
        this.kd = kd;

        this.setpoint = setpoint;
    }

    public double compute(double currentUtilization) {
        double error = setpoint - currentUtilization; // 计算偏差
        integral += error; // 更新积分项
        double derivative = error - lastError; // 计算微分项
        lastError = error;
        // 计算PID输出
        return kp * error + ki * integral + kd * derivative;
    }

}
