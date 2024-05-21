package io.github.workload.aqm;

import io.github.workload.annotations.ThreadSafe;

import java.util.concurrent.atomic.AtomicReference;

/**
 * PID控制器，用于实现比例-积分-微分（PID）控制。
 * <p>
 * PID控制器是一种常见的反馈控制器，广泛应用于工业控制系统中。
 * 它通过计算设定点（期望值）与实际测量值之间的偏差，使用比例（P）、积分（I）和微分（D）三个参数
 * 来调整控制输入，以达到快速响应、减少偏差和稳定系统的目的。
 * </p>
 */
@ThreadSafe
public class PIDController {
    private final double kp; // 比例增益
    private final double ki; // 积分增益
    private final double kd; // 微分增益

    private final double setpoint; // 设定点，即期望值

    private volatile double lastError; // 上一次的偏差
    private final AtomicReference<Double> integral = new AtomicReference<>(0d); // 积分项累计

    /**
     * 构造一个新的PID控制器实例。
     *
     * @param kp       比例增益，控制偏差的比例响应。
     * @param ki       积分增益，控制过去偏差累积的响应。
     * @param kd       微分增益，控制偏差变化率的响应。
     * @param setpoint 控制器的设定点，即期望的系统输出值。
     */
    public PIDController(double kp, double ki, double kd, double setpoint) {
        // Ziegler-Nichols方法调整增益参数，过载保护的起始值：(0.5, 0.01, 0.1)
        this.kp = kp;
        this.ki = ki;
        this.kd = kd;

        this.setpoint = setpoint;
    }

    /**
     * 根据当前采样值计算并返回PID控制器的输出。
     * <p>
     * 此方法计算当前采样值与设定点之间的偏差，然后基于PID参数（比例、积分、微分增益）
     * 计算控制输出。这个输出可以用来调整被控系统，以减少偏差并稳定系统。
     * </p>
     *
     * @param measurement 当前系统的实际测量值
     * @return 控制信号，即调整系数，[0, 1]。0表示不需要调整：期望值与实际值完全相同
     */
    public double update(double measurement) {
        final double error = setpoint - measurement; // 计算偏差
        integral.updateAndGet(value -> value + error);

        double derivative;
        synchronized (this) {
            derivative = error - lastError; // 计算微分项
            lastError = error; // 更新上一次的偏差
        }

        // 比例控制P，是最直接的控制方式，它根据系统当前的误差来调整输出。误差越大，控制器的调整幅度也越大
        final double P = kp * error;

        // 积分控制I，考虑了误差随时间的累积效应。
        // 如果系统存在稳态误差，即使P已经达到最大或最小输出，系统仍然无法达到目标值
        // 积分控制通过累积误差来调整输出，以消除稳态误差
        final double I = ki * integral.get();

        //微分控制D，关注误差的变化率，即系统输出的变化趋势
        // 通过预测误差的未来变化，微分控制可以提高系统的响应速度，减少超调和振荡
        final double D = kd * derivative;

        final double controlSignal = P + I + D;
        return normalizeControlSignal(controlSignal);
    }

    protected double normalizeControlSignal(double controlSignal) {
        return Math.max(0, Math.min(1, controlSignal));
    }

}
