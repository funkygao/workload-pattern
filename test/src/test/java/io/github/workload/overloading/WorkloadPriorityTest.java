package io.github.workload.overloading;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

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
    void ofStableRandomU() {
        WorkloadPriority priority = WorkloadPriority.ofStableRandomU(2, "34_2323".hashCode());
        assertEquals(2, priority.B());
        assertEquals(100, priority.U());
        priority = WorkloadPriority.ofStableRandomU(5, 0);
        assertEquals(5, priority.B());
        assertEquals(0, priority.U());
        priority = WorkloadPriority.ofStableRandomU(9, -1);
        assertEquals(9, priority.B());
        assertEquals(7, priority.U());
        priority = WorkloadPriority.ofStableRandomU(10, Integer.MIN_VALUE);
        assertEquals(0, priority.U());
        priority = WorkloadPriority.ofStableRandomU(10, Integer.MAX_VALUE);
        assertEquals(7, priority.U());

        priority = WorkloadPriority.ofStableRandomU(0, 1);
        assertEquals(0, priority.B());
        priority = WorkloadPriority.ofStableRandomU("listBooks".hashCode(), 5);
        assertEquals(1, priority.B());
        priority = WorkloadPriority.ofStableRandomU("getBook".hashCode(), 5);
        assertEquals(0, priority.B());
        priority = WorkloadPriority.ofStableRandomU(-10, 1);
        assertEquals(125, priority.B());
        priority = WorkloadPriority.ofStableRandomU(Integer.MAX_VALUE, 1);
        assertEquals(7, priority.B());
        priority = WorkloadPriority.ofStableRandomU(Integer.MIN_VALUE, 1);
        assertEquals(0, priority.B());
        assertEquals(1, priority.U());
    }

    @Test
    void randomUnchangedWithinHour() {
        String uIdentifier = "34_2323";
        WorkloadPriority priority = WorkloadPriority.ofStableRandomU(2, uIdentifier.hashCode());
        for (int i = 0; i < 1000; i++) {
            WorkloadPriority priority1 = WorkloadPriority.ofStableRandomU(2, uIdentifier.hashCode());
            // 这些肯定在1h内执行完毕，1h内U不变
            assertEquals(priority.U(), priority1.U());
            assertEquals(priority.B(), priority1.B());
            assertEquals(2, priority1.B());
        }
    }

    @Test
    @RepeatedTest(20)
    void timeRandomU() throws InterruptedException {
        String uIdentifier = "34_2323";
        Set<Integer> uniqueU = new HashSet<>();
        int N = 10;
        for (int i = 0; i < N; i++) {
            WorkloadPriority priority = WorkloadPriority.timeRandomU(1, uIdentifier.hashCode(), 5);
            Thread.sleep(3);
            System.out.printf("T:%d %s\n", Thread.currentThread().getId(), priority);
            uniqueU.add(priority.U());
        }

        assertTrue(N > uniqueU.size() && uniqueU.size() > 3);
    }

}
