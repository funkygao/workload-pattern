package io.github.workload.overloading;

import io.github.workload.WorkloadPriority;

import java.util.ArrayDeque;

class WatermarkHistory {
    private final ArrayDeque<Double> shedRatios;
    private final ArrayDeque<WorkloadPriority> watermarks;

    WatermarkHistory(int histories) {
        shedRatios = new ArrayDeque<>(histories);
        watermarks = new ArrayDeque<>(histories);
        for (int i = 0; i < histories; i++) {
            shedRatios.addLast(1d);
            watermarks.addLast(WorkloadPriority.ofLowest());
        }
    }

    void addHistory(double shedRatio, WorkloadPriority watermark) {
        shedRatios.pollFirst();
        shedRatios.addLast(shedRatio);

        watermarks.pollFirst();
        watermarks.addLast(watermark);
    }

    double averageShedRatio() {
        return shedRatios.stream().mapToDouble(d -> d).average().orElse(0.0);
    }

    WorkloadPriority lastWatermark() {
        return watermarks.peekFirst();
    }
}
