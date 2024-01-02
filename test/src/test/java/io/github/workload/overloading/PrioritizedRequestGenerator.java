package io.github.workload.overloading;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

class PrioritizedRequestGenerator {
    private Map<Integer /* P */, Integer /* 该P的请求数量 */> map = new HashMap<>();

    Set<Integer> priorities() {
        return map.keySet();
    }

    int requestsOfP(int P) {
        return map.get(P);
    }

    PrioritizedRequestGenerator fullyRandomize(int requestBound) {
        for (int P = 0; P <= WorkloadPriority.MAX_P; P++) {
            map.put(P, ThreadLocalRandom.current().nextInt(requestBound));
        }
        return this;
    }

    int totalRequests() {
        int total = 0;
        for (Integer P : priorities()) {
            total += requestsOfP(P);
        }
        return total;
    }
}
