package io.github.workload.overloading;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

class PrioritizedRequestGenerator implements Iterable<Map.Entry<WorkloadPriority, Integer>> {
    private Map<WorkloadPriority, Integer /* 该P的请求数量 */> map = new HashMap<>();

    PrioritizedRequestGenerator fullyRandomize(int requestBound) {
        for (int P = 0; P <= WorkloadPriority.MAX_P; P++) {
            map.put(WorkloadPriority.fromP(P), ThreadLocalRandom.current().nextInt(requestBound));
        }
        return this;
    }

    int totalRequests() {
        int total = 0;
        for (Integer request : map.values()) {
            total += request;
        }
        return total;
    }

    PrioritizedRequestGenerator reset() {
        map.clear();
        return this;
    }

    PrioritizedRequestGenerator generateFewRequests() {
        for (int i = 0; i < 5; i++) {
            int P = ThreadLocalRandom.current().nextInt(WorkloadPriority.MAX_P);
            map.put(WorkloadPriority.fromP(P), i + 2);
        }
        return this;
    }

    @Override
    public Iterator<Map.Entry<WorkloadPriority, Integer>> iterator() {
        return map.entrySet().iterator();
    }
}
