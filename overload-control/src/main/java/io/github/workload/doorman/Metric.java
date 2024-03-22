package io.github.workload.doorman;

import io.github.workload.annotations.TestCoverageExcluded;
import io.github.workload.annotations.PoC;
import lombok.Generated;

import java.util.concurrent.atomic.LongAdder;

@PoC
@Generated
@TestCoverageExcluded
class Metric {

    /**
     * The number of requests attempted by the application layer at the client.
     */
    private final LongAdder requests = new LongAdder();

    /**
     * The number of requests accepted by the backend.
     */
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
