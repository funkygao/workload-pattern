package io.github.workload.greedy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GreedyConfigTest {

    @Test
    void newDefault() {
        GreedyConfig config = GreedyConfig.newDefault();
        assertEquals(100, config.getBatchSize());
        assertEquals(1000, config.getItemsLimit());
    }

    @Test
    void basic() {
        GreedyConfig config = new GreedyConfig.Builder()
                .onItemsLimitExceed(itemsProcessed -> {
                })
                .itemsLimit(500)
                .batchSize(120)
                .throttle(2000, "foo", new MockGreedyLimiter())
                .build();
        assertEquals(120, config.getBatchSize());
        assertEquals(500, config.getItemsLimit());
        assertEquals(2000, config.getRateLimitOnCostExceed());
        assertEquals("foo", config.getRateLimiterKey());
    }

}