package io.github.workload.helper;

import io.github.workload.WorkloadPriority;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class PrioritizedRequestGenerator implements Iterable<Map.Entry<WorkloadPriority, Integer>> {
    private Map<WorkloadPriority, Integer /* 该P的请求数量 */> requests = new HashMap<>();

    public PrioritizedRequestGenerator generateFullyRandom(int requestBound) {
        for (int P = 0; P <= WorkloadPriority.MAX_P; P++) {
            requests.put(WorkloadPriority.fromP(P), ThreadLocalRandom.current().nextInt(requestBound));
        }
        return this;
    }

    public PrioritizedRequestGenerator generateFewRequests() {
        for (int i = 0; i < 5; i++) {
            int P = ThreadLocalRandom.current().nextInt(WorkloadPriority.MAX_P);
            requests.put(WorkloadPriority.fromP(P), i + 2);
        }
        return this;
    }

    public PrioritizedRequestGenerator simulateRpcRequests(int N) {
        for (int i = 0; i < N; i++) {
            increment(WorkloadPrioritizer.randomRpc());
        }

        return this;
    }

    public PrioritizedRequestGenerator simulateMixedRequests(int N) {
        for (int i = 0; i < N; i++) {
            switch (ThreadLocalRandom.current().nextInt(4)) {
                case 0:
                    increment(WorkloadPrioritizer.randomMQ());
                    break;
                case 1:
                    increment(WorkloadPrioritizer.randomWeb());
                    break;
                case 2:
                    increment(WorkloadPrioritizer.randomLowPriority());
                    break;
                default:
                    increment(WorkloadPrioritizer.randomRpc());
                    break;
            }
        }
        return this;
    }

    public int totalRequests() {
        int total = 0;
        for (Integer request : requests.values()) {
            total += request;
        }
        return total;
    }

    public PrioritizedRequestGenerator reset() {
        requests.clear();
        return this;
    }

    private void increment(WorkloadPriority priority) {
        if (!requests.containsKey(priority)) {
            requests.put(priority, 0);
        }
        requests.put(priority, 1 + requests.get(priority));
    }

    @Override
    public Iterator<Map.Entry<WorkloadPriority, Integer>> iterator() {
        return requests.entrySet().iterator();
    }
}
