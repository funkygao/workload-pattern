package io.github.workload.overloading;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientSideAdaptiveThrottlerTest {

    ClientSideAdaptiveThrottler throttler = new ClientSideAdaptiveThrottler();

    @Test
    void localRejectProbability() {
        assertEquals(0.59, throttler.rejectProbability(1000, 200), 0.01);
        assertEquals(0.19, throttler.rejectProbability(1000, 400), 0.01);
        assertEquals(0, throttler.rejectProbability(1000, 500), 0.01);
        assertEquals(0.74, throttler.rejectProbability(1000, 500, .5), 0.01);
    }

}