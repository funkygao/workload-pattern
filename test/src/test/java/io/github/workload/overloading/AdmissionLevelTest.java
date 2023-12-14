package io.github.workload.overloading;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AdmissionLevelTest {

    @Test
    void ofAdmitAll() {
        AdmissionLevel level = AdmissionLevel.ofAdmitAll();
        assertNotSame(level, AdmissionLevel.ofAdmitAll());
        assertTrue(level.admit(WorkloadPriority.ofLowestPriority()));
        assertTrue(level.admit(WorkloadPriority.of(1, 2)));
        assertTrue(level.admit(WorkloadPriority.timeRandomU(90, "foo".hashCode(), 100)));
        assertEquals(32639, level.P());
    }

    @Test
    void changeTo() {
        AdmissionLevel level = AdmissionLevel.ofAdmitAll();
        WorkloadPriority priority = WorkloadPriority.timeRandomU(1, 1, 120);
        assertTrue(level.admit(priority));
        level.changeTo(WorkloadPriority.of(0, 0));
        assertEquals(0, level.P());
        assertFalse(level.admit(priority));

        level.changeTo(WorkloadPriority.of(3, 20));
        assertTrue(level.admit(WorkloadPriority.of(3, 19)));
        assertFalse(level.admit(WorkloadPriority.of(3, 21)));
        assertTrue(level.admit(WorkloadPriority.of(1, 19)));
        assertFalse(level.admit(WorkloadPriority.of(5, 19)));
    }

    @Test
    void testToString() {
        AdmissionLevel level = AdmissionLevel.ofAdmitAll();
        assertEquals("AdmissionLevel(B=127,U=127;P=32639)", level.toString());
        level.changeTo(WorkloadPriority.of(5, 9));
        assertEquals("AdmissionLevel(B=5,U=9;P=1289)", level.toString());
    }
}

