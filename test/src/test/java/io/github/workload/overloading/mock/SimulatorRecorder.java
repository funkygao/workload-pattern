package io.github.workload.overloading.mock;

import io.github.workload.WorkloadPriority;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模拟器的录制功能，以便在回放时可以比较不同参数的效果.
 */
public class SimulatorRecorder {
    private final Map<Long, List<WorkloadPriority>> threadWorkloads = new ConcurrentHashMap<>();
    private final Map<Long, List<Long>> threadLatencies = new ConcurrentHashMap<>();

    public void recordWorkloads(long threadId, List<WorkloadPriority> priorities) {
        threadWorkloads.computeIfAbsent(threadId, key -> new ArrayList<>())
                .addAll(priorities);
    }

    public void recordLatencies(long threadId, long latency) {
        threadLatencies.computeIfAbsent(threadId, key -> new ArrayList<>())
                .add(latency);
    }
}
