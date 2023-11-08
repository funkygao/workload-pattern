package io.github.workload.overloading;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdmissionLevelTest {

    @Test
    void ofAdmitAll() {
        AdmissionLevel level = AdmissionLevel.ofAdmitAll();
        assertTrue(level.admit(WorkloadPriority.ofLowestPriority()));
        assertTrue(level.admit(WorkloadPriority.of(1, 2)));

        assertEquals(32639, level.P());
    }

    @Test
    void changeTo() {
        AdmissionLevel level = AdmissionLevel.ofAdmitAll();
        level.changeTo(WorkloadPriority.ofExempt());
        assertEquals(0, level.P());

    }
}

