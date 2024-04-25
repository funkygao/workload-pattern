package io.github.workload.doorman;

import java.util.concurrent.atomic.LongAdder;

class Metric {
    private final LongAdder requests = new LongAdder();
    private final LongAdder accepts = new LongAdder();

    void localPass(boolean allow) {
        requests.increment();
        if (allow) {
            accepts.increment();
        }
    }

    void backendRejected() {
        if (accepts.intValue() > 0) {
            accepts.decrement();
        }
    }

    void reset() {
        accepts.reset();
        requests.reset();
    }

    int accepts() {
        return accepts.intValue();
    }

    int requests() {
        return requests.intValue();
    }

    @Override
    public String toString() {
        return "Metric(requests=" + requests() + ",accepts=" + accepts() + ")";
    }

}
