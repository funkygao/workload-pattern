package io.github.workload.aqm;

import io.github.workload.annotations.ThreadSafe;

import java.util.concurrent.atomic.AtomicLong;
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

    private volatile double lastError; // 上一次的偏差
    private final AtomicReference<Double> integral = new AtomicReference<>(0d); // 积分项累计
    private final AtomicLong lastTimeNs = new AtomicLong(-1);

    /**
     * 构造一个新的PID控制器实例。
     *
     * @param kp 比例增益，控制偏差的比例响应
     * @param ki 积分增益，控制过去偏差累积的响应
     * @param kd 微分增益，控制偏差变化率的响应
     */
    public PIDController(double kp, double ki, double kd) {
        // Ziegler-Nichols方法调整增益参数，过载保护的起始值：(0.5, 0.01, 0.1)
        this.kp = kp;
        this.ki = ki;
        this.kd = kd;
    }

    /**
     * 根据当前的误差和时间计算PID控制器的输出。
     * 输出值的范围和符号取决于误差大小、PID增益参数以及误差随时间的变化。
     *
     * @param error 当前的误差，表示目标状态和当前状态之间的差距。
     * @param nowNs 当前时间（纳秒），用于计算时间间隔（dt）。
     * @return 控制器输出，该值指示为了减少误差需要对系统进行的调整。
     * 输出值的大小表示调整的幅度，符号（正或负）表示调整的方向。
     * 例如，在一个加热系统中，正值可能意味着需要增加加热量，而负值可能意味着需要减少加热量。
     * 输出值的范围没有固定限制，取决于PID参数和输入误差的大小。
     */
    public double getOutput(double error, long nowNs) {
        integral.updateAndGet(value -> value + error);

        final long lastNs = lastTimeNs.get();
        // 第一次调用不计算dt
        double dt = (lastNs == -1) ? 0 : (nowNs - lastNs) / 1e9; // 将纳秒转换为秒
        lastTimeNs.set(nowNs);

        // 积分项需要考虑到时间（只有在时间更新时才积分）
        if (dt > 0) {
            integral.updateAndGet(i -> i + error * dt);
        }

        double derivative;
        synchronized (this) {
            derivative = (dt > 0) ? ((error - lastError) / dt) : 0; // 计算微分项
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

        return P + I + D;
    }

}
