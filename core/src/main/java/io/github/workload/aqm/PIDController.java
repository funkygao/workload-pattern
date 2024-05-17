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

    public double compute(double sample) {
        double error = setpoint - sample; // 计算偏差
        integral += error; // 更新积分项
        double derivative = error - lastError; // 计算微分项
        lastError = error;

        // 比例控制P，是最直接的控制方式，它根据系统当前的误差来调整输出。误差越大，控制器的调整幅度也越大
        double P = kp * error;
        // 积分控制I，考虑了误差随时间的累积效应。
        // 如果系统存在稳态误差，即使P已经达到最大或最小输出，系统仍然无法达到目标值
        // 积分控制通过累积误差来调整输出，以消除稳态误差
        double I = ki * integral;
        //微分控制D，关注误差的变化率，即系统输出的变化趋势
        // 通过预测误差的未来变化，微分控制可以提高系统的响应速度，减少超调和振荡
        double D = kd * derivative;
        return P + I + D;
    }

}
