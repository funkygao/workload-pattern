package io.github.workload.greedy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GreedyConfigTest {

    @Test
    void newDefault() {
        GreedyConfig config = GreedyConfig.newDefault();
        assertEquals(100, config.getPartitionSize());
        assertEquals(1000, config.getGreedyThreshold());
    }

}