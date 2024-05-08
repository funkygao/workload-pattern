package com.netflix;

import com.netflix.concurrency.limits.limit.Gradient2Limit;
import com.netflix.concurrency.limits.limit.VegasLimit;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * https://github.com/Netflix/concurrency-limits
 *
 * @see <a href="https://netflixtechblog.medium.com/performance-under-load-3e6fa9a60581">Performance Under Load</a>
 */
class ConcurrencyEvaluationTest {

    @Test
    void Gradient2Limit_demo() {
        Gradient2Limit limit = Gradient2Limit.newDefault();
        limit = Gradient2Limit.newBuilder()
                .maxConcurrency(200)
                .build();
        limit.onSample(0, TimeUnit.SECONDS.toNanos(10), 5000, false);
        assertEquals(20, limit.getLimit());
        limit.onSample(0, TimeUnit.SECONDS.toNanos(10), 6000, false);
        assertEquals(21, limit.getLimit());
    }

    @Test
    void VegasLimit_demo() {
        VegasLimit limit = VegasLimit.newBuilder()
                .initialLimit(10000)
                .maxConcurrency(20000)
                .build();
        limit.onSample(0, TimeUnit.SECONDS.toNanos(10), 5000, false);
        assertEquals(10000, limit.getLimit());
        limit.onSample(0, TimeUnit.SECONDS.toNanos(10), 6000, false);
        assertEquals(10024, limit.getLimit());
        limit.onSample(0, TimeUnit.SECONDS.toNanos(20), 3000, true);
        assertEquals(10020, limit.getLimit());
    }

}
