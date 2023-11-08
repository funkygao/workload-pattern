package io.github.workload.overloading;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class WorkloadPriorityTest {

    @Test
    void constructor() {
        try {
            WorkloadPriority.of(128, 1);
            fail();
        } catch (IllegalArgumentException expected) {
            assertEquals("Out of range for B or U", expected.getMessage());
        }

        try {
            WorkloadPriority.of(3, 1 << 8);
            fail();
        } catch (IllegalArgumentException expected) {
            assertEquals("Out of range for B or U", expected.getMessage());
        }

        WorkloadPriority.of(8, 36);
    }

    @Test
    void P() {
        WorkloadPriority p1 = WorkloadPriority.of(5, 3);
        WorkloadPriority p2 = WorkloadPriority.of(8, 10);
        assertEquals(1283, p1.P());
        assertEquals(2048 + 10, p2.P());
    }

    @Test
    void fromP() {
        WorkloadPriority priority = WorkloadPriority.fromP(1894);
        assertEquals(7, priority.B());
        assertEquals(102, priority.U());
        assertEquals(1894, priority.P());
        priority = WorkloadPriority.fromP(0);
        assertEquals(0, priority.B());
        assertEquals(0, priority.U());
        priority = WorkloadPriority.fromP(32639);
        assertEquals(127, priority.B());
        assertEquals(127, priority.U());
        try {
            WorkloadPriority.fromP(-1);
            fail();
        } catch (IllegalArgumentException expected) {
            assertEquals("Invalid P", expected.getMessage());
        }
        try {
            WorkloadPriority.fromP(32640);
            fail();
        } catch (IllegalArgumentException expected) {
            assertEquals("Invalid P", expected.getMessage());
        }
    }

    @Test
    void lowestPriority() {
        WorkloadPriority workloadPriority = WorkloadPriority.ofLowestPriority();
        assertEquals(127, workloadPriority.U());
        assertEquals(127, workloadPriority.U());
        assertEquals(32639, workloadPriority.P());
    }

    @Test
    void exempt() {
        WorkloadPriority exempt = WorkloadPriority.ofExempt();
        Random random = new Random();
        for (int i = 0; i < 10000; i++) {
            WorkloadPriority that = WorkloadPriority.of(random.nextInt(5), random.nextInt(5));
            assertTrue(exempt.P() <= that.P());
        }
    }

    @Test
    void ofRandom() {
        WorkloadPriority priority = WorkloadPriority.ofRandom(2, "34_2323".hashCode());
        assertEquals(2, priority.B());
        assertEquals(100, priority.U());
        priority = WorkloadPriority.ofRandom(5, 0);
        assertEquals(5, priority.B());
        assertEquals(0, priority.U());
        priority = WorkloadPriority.ofRandom(9, -1);
        assertEquals(9, priority.B());
        assertEquals(7, priority.U());
        priority = WorkloadPriority.ofRandom(10, Integer.MIN_VALUE);
        assertEquals(0, priority.U());
        priority = WorkloadPriority.ofRandom(10, Integer.MAX_VALUE);
        assertEquals(7, priority.U());

        priority = WorkloadPriority.ofRandom(0, 1);
        assertEquals(0, priority.B());
        priority = WorkloadPriority.ofRandom("listBooks".hashCode(), 5);
        assertEquals(1, priority.B());
        priority = WorkloadPriority.ofRandom("getBook".hashCode(), 5);
        assertEquals(0, priority.B());
        priority = WorkloadPriority.ofRandom(-10, 1);
        assertEquals(125, priority.B());
        priority = WorkloadPriority.ofRandom(Integer.MAX_VALUE, 1);
        assertEquals(7, priority.B());
        priority = WorkloadPriority.ofRandom(Integer.MIN_VALUE, 1);
        assertEquals(0, priority.B());
        assertEquals(1, priority.U());
    }

}
