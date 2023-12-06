package io.github.workload.overloading;

import org.junit.jupiter.api.Test;

class PIEControllerTest {

    @Test
    void basic() {
        double kp = 0.5;  // 比例增益
        double ki = 0.2;  // 积分增益

        PIEController controller = new PIEController(kp, ki);
        controller.setSetpoint(10.0);  // Set the desired setpoint

        double processValue = 0.0;  // Initial process value
        double dt = 0.1;  // Time step

        for (int i = 0; i < 100; i++) {
            double controlSignal = controller.calculate(processValue, dt);
            System.out.println("Control signal: " + controlSignal);

            // Update process value using the control signal
            processValue += controlSignal * dt;
        }
    }

}