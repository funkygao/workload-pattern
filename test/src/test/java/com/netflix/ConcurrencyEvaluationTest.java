package com.netflix;

import com.netflix.concurrency.limits.limit.Gradient2Limit;
import org.junit.jupiter.api.Test;

/**
 * https://github.com/Netflix/concurrency-limits
 *
 * @see <a href="https://netflixtechblog.medium.com/performance-under-load-3e6fa9a60581">Performance Under Load</a>
 */
class ConcurrencyEvaluationTest {

    @Test
    void Gradient2Limit_demo() {
        Gradient2Limit limit = Gradient2Limit.newDefault();
    }

}
