package io.github.workload.control;

import io.github.workload.annotations.ThreadSafe;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * PID控制器，用于实现比例-积分-微分（PID）控制.
 * <p>
 * <p>只要被控制量与输入量之间存在单调关系的反馈场景基本上都能用，以达到快速响应、减少偏差和稳定系统的目的.</p>
 * <p>例如：云计算场景PID用于动态优化资源分配，以应对不断变化的负载和用户需求.</p>
 * <p>例如，短视频场景PID用于推荐算法的优化，目标是最大化用户的观看时间.</p>
 */
@ThreadSafe
public class PIDController {
    private final double kp; // 比例增益
    private final double ki; // 积分增益
    private final double kd; // 微分增益
    private final boolean ignoreDt;

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
        this(kp, ki, kd, false);
    }

    public PIDController(double kp, double ki, double kd, boolean ignoreDt) {
        this.kp = kp;
        this.ki = ki;
        this.kd = kd;
        this.ignoreDt = ignoreDt;
    }

    /**
     * 根据当前的误差和时间计算PID控制器的输出。
     * 输出值的范围和符号取决于误差大小、PID增益参数以及误差随时间的变化。
     *
     * @param error 偏差，表示目标状态和当前状态之间的差距，来自过程变量
     * @param nowNs 当前时间（纳秒），用于计算时间间隔（dt）
     * @return 控制器输出，用于调整控制量
     */
    public double getOutput(double error, long nowNs) {
        integral.updateAndGet(value -> value + error);

        // 时间维度
        double dt;
        if (ignoreDt) {
            dt = 1;
        } else {
            final long lastNs = lastTimeNs.get();
            lastTimeNs.set(nowNs);
            // 第一次调用不计算dt
            dt = (lastNs == -1) ? 0 : (nowNs - lastNs) / 1e9; // 将纳秒转换为秒
        }

        // 比例控制P，快速响应，但也容易导致用力过猛，可能导致系统过冲或在目标点附近振荡
        final double P = kp * error;

        // 积分控制I，通过累积过去的误差来调整输出，可以帮助消除系统的静态误差，确保长期稳定
        if (dt > 0) {
            // 积分项需要考虑到时间：只有在时间更新时才积分
            integral.updateAndGet(i -> i + error * dt);
        }
        final double I = ki * integral.get();

        // 微分控制D，基于误差变化率(速度)，预测误差的未来趋势，通过减少系统的响应速度来防止过冲
        double derivative;
        synchronized (this) {
            derivative = (dt > 0) ? ((error - lastError) / dt) : 0; // 计算微分项
            lastError = error; // 更新上一次的偏差
        }
        final double D = kd * derivative;

        return P + I + D;
    }

}
