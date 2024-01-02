package io.github.workload.overloading;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AdmissionLevelTest {

    @Test
    void ofAdmitAll() {
        AdmissionLevel level = AdmissionLevel.ofAdmitAll();
        assertNotSame(level, AdmissionLevel.ofAdmitAll());
        assertTrue(level.admit(WorkloadPriority.ofLowestPriority()));
        assertTrue(level.admit(WorkloadPriority.of(1, 2)));
        assertTrue(level.admit(WorkloadPriority.ofPeriodicRandomFromUID(90, "foo".hashCode(), 100)));
        assertEquals(WorkloadPriority.MAX_P, level.P());
    }

    @Test
    void immutable() {
        AdmissionLevel level = AdmissionLevel.ofAdmitAll();
        assertNotSame(level, AdmissionLevel.ofAdmitAll());
        int P = level.P();
        level.switchTo(WorkloadPriority.of(1, 2));
        assertEquals(P, level.P());
        assertNotEquals(P, level.switchTo(WorkloadPriority.of(1, 2)).P());
        assertEquals(WorkloadPriority.ofLowestPriority().P(), level.P());
        assertEquals(WorkloadPriority.of(5, 6).P(), level.switchTo(WorkloadPriority.of(5, 6)).P());
    }

    @Test
    void switchTo() {
        AdmissionLevel level = AdmissionLevel.ofAdmitAll();
        // P 没变，则返回当前实例
        assertSame(level, level.switchTo(WorkloadPriority.ofLowestPriority()));
        WorkloadPriority priority = WorkloadPriority.ofPeriodicRandomFromUID(1, 1, 120);
        assertTrue(level.admit(priority));
        level.switchTo(WorkloadPriority.of(0, 0));
        // changeTo will not change level, but return a new level
        assertEquals(WorkloadPriority.ofLowestPriority().P(), level.P());
        AdmissionLevel level1 = level.switchTo(WorkloadPriority.of(0, 0));
        assertEquals(0, level1.P());

        level = level.switchTo(WorkloadPriority.of(3, 20));
        assertTrue(level.admit(WorkloadPriority.of(3, 19)));
        assertFalse(level.admit(WorkloadPriority.of(3, 21)));
        assertTrue(level.admit(WorkloadPriority.of(1, 19)));
        assertFalse(level.admit(WorkloadPriority.of(5, 19)));
    }

    @Test
    void test_equals_and_hash() {
        AdmissionLevel level = new AdmissionLevel(WorkloadPriority.of(1, 2));
        assertEquals(level, level);
        assertFalse(level.equals(WorkloadPriority.of(1, 2)));
        AdmissionLevel level1 = new AdmissionLevel(WorkloadPriority.of(1, 2));
        assertEquals(level, level1);
        assertEquals(level.hashCode(), level1.hashCode());
        Set<AdmissionLevel> levels = new HashSet<>();
        levels.add(level);
        levels.add(level1);
        assertEquals(1, levels.size());
    }

    @Test
    void testToString() {
        AdmissionLevel level = AdmissionLevel.ofAdmitAll();
        assertEquals("AdmissionLevel(B=127,U=127;P=16383)", level.toString());
        level = level.switchTo(WorkloadPriority.of(5, 9));
        assertEquals("AdmissionLevel(B=5,U=9;P=649)", level.toString());
    }
}

