package io.github.workload.simd;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

class StreamSIMDEnhancerTest {

    @Test
    void basic() {
        List<Long> numbers = Arrays.asList(10L, 5L, 15L, 20L, 3L, 8L, 25L);

        long maxId = StreamSIMDEnhancer.simdEnhance(numbers.stream())
                .mapToLong(Long::longValue)
                .max()
                .orElse(0L);

        System.out.println("Max ID: " + maxId);
    }

}