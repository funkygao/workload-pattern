package io.github.workload.overloading;

import io.github.workload.WorkloadPriority;

import java.util.LinkedList;
import java.util.Queue;

class WatermarkHistory {
    static final int MAX_HISTORY_SIZE = 5; // 保存最近5个周期的历史数据
    Queue<Double> shedRatios = new LinkedList<>();
    Queue<WorkloadPriority> watermarks = new LinkedList<>();

    void addHistory(double shedRatio, WorkloadPriority watermark) {
        if (shedRatios.size() >= MAX_HISTORY_SIZE) {
            shedRatios.poll();
            watermarks.poll();
        }
        shedRatios.add(shedRatio);
        watermarks.add(watermark);
    }

    double averageShedRatio() {
        return shedRatios.stream().mapToDouble(d -> d).average().orElse(0.0);
    }

    WorkloadPriority lastWatermark() {
        return watermarks.peek();
    }
}
