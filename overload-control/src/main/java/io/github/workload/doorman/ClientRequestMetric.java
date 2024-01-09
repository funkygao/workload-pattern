package io.github.workload.doorman;

import java.util.concurrent.atomic.LongAdder;

class Foo {
    private final LongAdder accepts = new LongAdder();
    private final LongAdder requests = new LongAdder();

    Foo() {

    }

    int accepted() {
        return accepts.intValue();
    }

    int requests() {
        return requests.intValue();
    }

    void reset() {
        accepts.reset();
        requests.reset();
    }

    void pass() {
        requests.increment();
        accepts.increment();
    }

    void fail() {
        requests.increment();
    }
}
