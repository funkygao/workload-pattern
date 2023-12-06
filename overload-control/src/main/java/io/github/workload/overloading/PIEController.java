package io.github.workload.overloading;

// Proportional Integral controller Enhanced algorithm
// 根据(当前排队长度，历史出队速率)，通过随机drop request来控制排队时间
// http://iheartradio.github.io/kanaloa/docs/theories.html
class PIEController {
    private double kp;  // 比例增益
    private double ki;  // 积分增益
    private double setpoint;  // 期望的设定值
    private double integralError;  // Integral error from previous iteration

    PIEController(double kp, double ki) {
        this.kp = kp;
        this.ki = ki;
        this.setpoint = 0.0;
        this.integralError = 0.0;
    }

    public void setSetpoint(double setpoint) {
        this.setpoint = setpoint;
    }

    // 计算控制信号
    public double calculate(double processValue, double dt /* time step */) {
        double error = setpoint - processValue;

        // Proportional term
        double proportional = kp * error;

        // Integral term
        integralError += error * dt;
        double integral = ki * integralError;

        // Control signal
        double controlSignal = proportional + integral;
        return controlSignal;
    }
}
