package io.github.workload.metrics.smoother;

import io.github.workload.BaseConcurrentTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LatestValueTest extends BaseConcurrentTest {

    @Test
    void basic() {
        ValueSmoother smoother = ValueSmoother.ofLatestValue();
        ValueSmoother smoother1 = smoother.update(1.3);
        assertSame(smoother1, smoother);
        assertEquals(1.3, smoother.smoothedValue(), DELTA);
        assertEquals(18.9, smoother.update(18.9).smoothedValue(), DELTA);
    }

}