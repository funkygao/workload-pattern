package io.github.workload.control;

import io.github.workload.annotations.NotThreadSafe;

import java.util.ArrayDeque;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Model Predictive Control.
 *
 * <ul>vs PID
 * <li>模型预测控制善于处理多输入多输出系统</li>
 * </ul>
 */
@NotThreadSafe
class MPCController {
    private final ArrayDeque<Double> predictions;
    private double shedProbability = 1d;

    public MPCController(int queueLength) {
        predictions = new ArrayDeque<>(queueLength);
        for (int i = 0; i < queueLength; i++) {
            predictions.addLast(1d);
        }
    }

    // 更新预测模型
    public MPCController updateModel(double data) {
        predictions.pollFirst(); // 移除旧的预测
        predictions.addLast(data); // 添加新的预测

        final double avg = predictions.stream().mapToDouble(i -> i).average().orElse(0.0);
        if (avg > 2) {
            shedProbability = Math.min(1.0, shedProbability + 0.1);
        } else {
            shedProbability = Math.max(0.0, shedProbability - 0.1);
        }
        return this;
    }

    public boolean shouldShed() {
        return ThreadLocalRandom.current().nextDouble() < shedProbability;
    }
}
