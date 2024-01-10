package io.github.workload.overloading;

import io.github.workload.WorkloadPriority;
import io.github.workload.WorkloadPriorityHelper;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AdmissionLevelTest {

    @Test
    void ofAdmitAll() {
        AdmissionLevel level = AdmissionLevel.ofAdmitAll();
        assertSame(level, AdmissionLevel.ofAdmitAll());
        assertTrue(level.admit(WorkloadPriority.ofLowest()));
        assertTrue(level.admit(WorkloadPriorityHelper.of(1, 2)));
        assertTrue(level.admit(WorkloadPriorityHelper.ofPeriodicRandomFromUID(90, "foo".hashCode(), 100)));
        assertEquals(WorkloadPriority.MAX_P, level.P());
    }

    @Test
    void immutable() {
        AdmissionLevel level = AdmissionLevel.ofAdmitAll();
        int P = level.P();
        level.changeBar(WorkloadPriorityHelper.of(1, 2).P());
        assertEquals(P, level.P());
        assertNotEquals(P, level.changeBar(WorkloadPriorityHelper.of(1, 2).P()).P());
        assertEquals(WorkloadPriority.ofLowest().P(), level.P());
        assertEquals(WorkloadPriorityHelper.of(5, 6).P(), level.changeBar(WorkloadPriorityHelper.of(5, 6).P()).P());
    }

    @Test
    void switchTo() {
        AdmissionLevel level = AdmissionLevel.ofAdmitAll();
        // P 没变，则返回当前实例
        assertSame(level, level.changeBar(WorkloadPriority.ofLowest().P()));
        WorkloadPriority priority = WorkloadPriorityHelper.ofPeriodicRandomFromUID(1, 1, 120);
        assertTrue(level.admit(priority));
        level.changeBar(WorkloadPriorityHelper.of(0, 0).P());
        // changeTo will not change level, but return a new level
        assertEquals(WorkloadPriority.ofLowest().P(), level.P());
        AdmissionLevel level1 = level.changeBar(WorkloadPriorityHelper.of(0, 0).P());
        assertEquals(0, level1.P());

        level = level.changeBar(WorkloadPriorityHelper.of(3, 20).P());
        assertTrue(level.admit(WorkloadPriorityHelper.of(3, 19)));
        assertFalse(level.admit(WorkloadPriorityHelper.of(3, 21)));
        assertTrue(level.admit(WorkloadPriorityHelper.of(1, 19)));
        assertFalse(level.admit(WorkloadPriorityHelper.of(5, 19)));
    }

    @Test
    void test_equals_and_hash() {
        AdmissionLevel level = new AdmissionLevel(WorkloadPriorityHelper.of(1, 2));
        assertEquals(level, level);
        assertFalse(level.equals(WorkloadPriorityHelper.of(1, 2)));
        AdmissionLevel level1 = new AdmissionLevel(WorkloadPriorityHelper.of(1, 2));
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
        level = level.changeBar(WorkloadPriorityHelper.of(5, 9).P());
        assertEquals("AdmissionLevel(B=5,U=9;P=649)", level.toString());
    }
}

