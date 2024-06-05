package io.github.workload.safe;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SafeGuardTest {

    @Test
    void basic() {
        SafeGuard guard = SafeGuard.builder().unsafeItemsThreshold(1000).build();
        assertEquals(1000, guard.getUnsafeItemsThreshold());
    }

}