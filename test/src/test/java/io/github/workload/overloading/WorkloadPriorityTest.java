package io.github.workload.overloading;

import org.junit.jupiter.api.Test;

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
        assertFalse(p1.equals(p2));
        assertTrue(p1.equals(WorkloadPriority.of(5, 3)));
        assertEquals(p1, WorkloadPriority.of(5, 3));
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

        // 随机数来验证fromP解析出来的(B, U)在合法区间
        for (int i = 0; i < 1000; i++) {
            TestingUtil.randomWorkloadPriority();
        }
    }

    @Test
    void consistentEncodeDecode() {
        for (int B = 0; B < 128; B++) {
            for (int U = 0; U < 128; U++) {
                WorkloadPriority priority = WorkloadPriority.of(B, U);
                WorkloadPriority priority1 = WorkloadPriority.fromP(priority.P());
                assertEquals(priority, priority1);
            }
        }
    }

    @Test
    void ofUid() {
        WorkloadPriority priority = WorkloadPriority.ofUid(2, "34_2323".hashCode());
        assertEquals(2, priority.B());
        priority = WorkloadPriority.ofUid(5, 0);
        priority = WorkloadPriority.ofUid(9, -1);
        priority = WorkloadPriority.ofUid(10, Integer.MIN_VALUE);
        priority = WorkloadPriority.ofUid(10, Integer.MAX_VALUE);

        priority = WorkloadPriority.ofUid(0, 1);
        assertEquals(0, priority.B());
        priority = WorkloadPriority.ofUid("listBooks".hashCode(), 5);
        assertEquals(1, priority.B());
        priority = WorkloadPriority.ofUid("getBook".hashCode(), 5);
        assertEquals(0, priority.B());
        priority = WorkloadPriority.ofUid(-10, 1);
        assertEquals(125, priority.B());
        priority = WorkloadPriority.ofUid(Integer.MAX_VALUE, 1);
        assertEquals(7, priority.B());
        priority = WorkloadPriority.ofUid(Integer.MIN_VALUE, 1);
        assertEquals(0, priority.B());
    }

    @Test
    void randomUnchangedWithinHour() {
        String uIdentifier = "34_2323";
        WorkloadPriority priority = WorkloadPriority.ofUid(2, uIdentifier.hashCode());
        for (int i = 0; i < 1000; i++) {
            WorkloadPriority priority1 = WorkloadPriority.ofUid(2, uIdentifier.hashCode());
            // 这些肯定在1h内执行完毕，1h内U不变
            assertEquals(priority.U(), priority1.U());
            assertEquals(priority.B(), priority1.B());
            assertEquals(2, priority1.B());
        }
    }

    @Test
    void testToString() {
        WorkloadPriority priority = WorkloadPriority.ofLowestPriority();
        assertEquals("WorkloadPriority(B=127, U=127)", priority.toString());
    }

}
