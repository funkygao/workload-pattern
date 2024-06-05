package io.github.workload;

import io.github.workload.helper.RandomUtil;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class WorkloadPriorityTest {

    @Test
    void constructor() {
        Exception expected = assertThrows(IllegalArgumentException.class, () -> {
            WorkloadPriority.of(128, 1);
        });
        assertEquals("Out of range for B or U", expected.getMessage());

        expected = assertThrows(IllegalArgumentException.class, () -> {
            WorkloadPriority.of(3, 1 << 8);
        });
        assertEquals("Out of range for B or U", expected.getMessage());

        expected = assertThrows(IllegalArgumentException.class, () -> {
            WorkloadPriority.of(0, -1);
        });
        assertEquals("Out of range for B or U", expected.getMessage());

        expected = assertThrows(IllegalArgumentException.class, () -> {
            WorkloadPriority.of(-88, 1);
        });
        assertEquals("Out of range for B or U", expected.getMessage());

        WorkloadPriority.of(8, 36);
        WorkloadPriority.of(0, 0);
        WorkloadPriority.of(127, 127);
    }

    @Test
    void tuneU() {
        WorkloadPriority priority = WorkloadPriority.of(WorkloadPriority.B_CRITICAL, 100);
        assertEquals(priority.B(), priority.tuneU(100).B());
        assertSame(priority, priority.tuneU(0));

        assertEquals(126, priority.tuneU(26).U());
        assertEquals(127, priority.tuneU(27).U());
        assertEquals(127, priority.tuneU(28).U());
        assertEquals(127, priority.tuneU(100).U());
        assertEquals(127, priority.tuneU(Integer.MAX_VALUE - 100).U());

        assertEquals(0, priority.tuneU(Integer.MAX_VALUE).U()); // overflow
        assertEquals(priority.B(), priority.tuneU(Integer.MAX_VALUE).B()); // overflow

        assertEquals(98, priority.tuneU(-2).U());
        assertEquals(80, priority.tuneU(-20).U());
        assertEquals(0, priority.tuneU(-100).U());
        assertEquals(0, priority.tuneU(-101).U());
        assertEquals(0, priority.tuneU(Integer.MIN_VALUE).U());
    }

    @Test
    void P() {
        WorkloadPriority p1 = WorkloadPriority.of(7, 3);
        WorkloadPriority p2 = WorkloadPriority.of(8, 10);
        assertEquals(899, p1.P());
        assertEquals(1024 + 10, p2.P());
        assertNotEquals(p1, p2);
        assertEquals(p1, WorkloadPriority.of(7, 3));
        assertNotSame(p1, WorkloadPriority.of(7, 3));
        assertEquals(p1, WorkloadPriority.of(7, 3));
    }

    @Test
    void test_immutable() {
        WorkloadPriority p1 = WorkloadPriority.fromP(234);
        assertNotSame(p1, WorkloadPriority.fromP(234));
        assertEquals(p1, WorkloadPriority.fromP(234));

        p1 = WorkloadPriority.ofPeriodicRandomFromUID(6, 345);
        assertNotSame(p1, WorkloadPriority.ofPeriodicRandomFromUID(6, 345));
        assertEquals(p1, WorkloadPriority.ofPeriodicRandomFromUID(6, 345));
    }

    @Test
    void test_warmPool() {
        WorkloadPriority p1 = WorkloadPriority.of(WorkloadPriority.B_CRITICAL, 4);
        WorkloadPriority p2 = WorkloadPriority.of(WorkloadPriority.B_CRITICAL, 4);
        assertSame(p1, p2);

        // 只有那几个固定B，才缓存
        WorkloadPriority p3 = WorkloadPriority.of(44, 4);
        WorkloadPriority p4 = WorkloadPriority.of(44, 4);
        assertNotSame(p3, p4);
        assertEquals(p3, p4);
    }

    @Test
    void test_isLowest() {
        WorkloadPriority p1 = WorkloadPriority.ofLowest();
        assertTrue(p1.isLowest());

        WorkloadPriority p2 = WorkloadPriority.fromP(WorkloadPriority.MAX_P);
        assertTrue(p2.isLowest());

        assertEquals(p1, p2);
    }

    @Test
    void fromP() {
        WorkloadPriority priority = WorkloadPriority.fromP(1894);
        assertEquals(14, priority.B());
        assertEquals(102, priority.U());
        assertEquals(1894, priority.P());
        priority = WorkloadPriority.fromP(0);
        assertEquals(0, priority.B());
        assertEquals(0, priority.U());
        priority = WorkloadPriority.fromP(WorkloadPriority.MAX_P);
        assertEquals(127, priority.B());
        assertEquals(127, priority.U());

        Exception expected = assertThrows(IllegalArgumentException.class, () -> {
            WorkloadPriority.fromP(-1);
        });
        assertEquals("Invalid P", expected.getMessage());

        expected = assertThrows(IllegalArgumentException.class, () -> {
            WorkloadPriority.fromP(32640);
        });
        assertEquals("Invalid P", expected.getMessage());

        // 随机数来验证fromP解析出来的(B, U)在合法区间，只要不抛出异常即可
        for (int i = 0; i < 1000; i++) {
            RandomUtil.randomWorkloadPriority();
        }

        // 验证 P 是连续的
        assertEquals(16383, WorkloadPriority.MAX_P);
        Set<WorkloadPriority> uniqueSet = new HashSet<>();
        for (int P = 0; P < WorkloadPriority.MAX_P; P++) {
            uniqueSet.add(WorkloadPriority.fromP(P));
            assertEquals(P, WorkloadPriority.fromP(P).P());
        }
        assertEquals(16383, uniqueSet.size());

        priority = WorkloadPriority.fromP(0);
        assertEquals(0, priority.P());
        assertEquals(0, priority.B());
        assertEquals(0, priority.U());
        priority = WorkloadPriority.fromP(128);
        assertEquals(128, priority.P());
        assertEquals(1, priority.B());
        assertEquals(0, priority.U());
        priority = WorkloadPriority.fromP(129);
        assertEquals(1, priority.B());
        assertEquals(1, priority.U());
    }

    @Test
    void derive() {
        WorkloadPriority priority = WorkloadPriority.ofLowest();
        WorkloadPriority priority1 = priority.deriveFromP(WorkloadPriority.MAX_P);
        assertSame(priority1, priority);

        priority = WorkloadPriority.of(1, 3);
        priority1 = priority.deriveFromP(WorkloadPriority.of(2, 1).P());
        assertNotEquals(priority1, priority);
        priority1 = priority.deriveFromP(WorkloadPriority.of(1, 3).P());
        assertSame(priority1, priority);
    }

    @Test
    void consistentEncodeDecode() {
        for (int B = 0; B < 128; B++) {
            for (int U = 0; U < 128; U++) {
                // 根据 (B, U)进行序列化
                WorkloadPriority coded = WorkloadPriority.of(B, U);
                // 反向，反序列化
                WorkloadPriority decoded = WorkloadPriority.fromP(coded.P());
                // 验证序列化后可反序列化
                assertEquals(coded, decoded, String.format("B=%d, U=%d", B, U));
            }
        }
    }

    @Test
    void ofPeriodicRandomFromUID() {
        WorkloadPriority priority = WorkloadPriority.ofPeriodicRandomFromUID(2, "94_238230".hashCode());
        assertEquals(2, priority.B());
        priority = WorkloadPriority.ofPeriodicRandomFromUID(5, 0);
        assertEquals(5, priority.B());
        priority = WorkloadPriority.ofPeriodicRandomFromUID(9, -1);
        assertEquals(9, priority.B());
        priority = WorkloadPriority.ofPeriodicRandomFromUID(10, Integer.MIN_VALUE);
        assertEquals(10, priority.B());
        priority = WorkloadPriority.ofPeriodicRandomFromUID(0, 1);
        assertEquals(0, priority.B());
        priority = WorkloadPriority.ofPeriodicRandomFromUID("listBooks".hashCode(), 5);
        assertEquals(1, priority.B());
        priority = WorkloadPriority.ofPeriodicRandomFromUID("getBook".hashCode(), 5);
        assertEquals(0, priority.B());
        priority = WorkloadPriority.ofPeriodicRandomFromUID(-10, 1);
        assertEquals(125, priority.B());
        // 允许 B 超过127：它会取模到[0, 127]
        priority = WorkloadPriority.ofPeriodicRandomFromUID(Integer.MAX_VALUE, 1);
        assertEquals(7, priority.B());
        priority = WorkloadPriority.ofPeriodicRandomFromUID(Integer.MIN_VALUE, 1);
        assertEquals(0, priority.B());
    }

    @RepeatedTest(100)
    void ofPeriodicRandomFromUID_negativeUid() {
        WorkloadPriority priority = WorkloadPriority.ofPeriodicRandomFromUID(3, -"bar".hashCode());
        assertEquals(3, priority.B());
        assertTrue(0 <= priority.U() && priority.U() <= 127);
    }

    @Test
    void randomUnchangedWithinHour() {
        String uIdentifier = "34_2323";
        WorkloadPriority priority = WorkloadPriority.ofPeriodicRandomFromUID(2, uIdentifier.hashCode());
        for (int i = 0; i < 1000; i++) {
            WorkloadPriority priority1 = WorkloadPriority.ofPeriodicRandomFromUID(2, uIdentifier.hashCode());
            // 这些肯定在1h内执行完毕，1h内U不变
            assertEquals(priority.U(), priority1.U());
            assertEquals(priority.B(), priority1.B());
            assertEquals(2, priority1.B());
            assertEquals(priority, priority1);
        }
    }

    @Test
    void testToString() {
        WorkloadPriority priority = WorkloadPriority.ofLowest();
        assertEquals("WorkloadPriority(B=127, U=127, P=16383)", priority.toString());
        priority = WorkloadPriority.fromP(198);
        assertEquals("WorkloadPriority(B=1, U=70, P=198)", priority.toString());
    }

    @Test
    void simpleString() {
        WorkloadPriority priority = WorkloadPriority.ofLowest();
        assertEquals("priority(lowest)", priority.simpleString());
        priority = WorkloadPriority.fromP(5138);
        assertEquals("priority(P=5138,B=40)", priority.simpleString());
        priority = WorkloadPriority.fromP(1391);
        assertEquals("priority(P=1391,B=10)", priority.simpleString());
    }

    @Test
    void test_equals() {
        WorkloadPriority priority = WorkloadPriority.of(1, 9);
        assertEquals(priority, priority);
        assertEquals(priority, WorkloadPriority.of(1, 9));
        assertNotEquals("", priority);
        assertEquals(priority, WorkloadPriority.of(1, 9));
        assertNotEquals(priority, WorkloadPriority.ofLowest());
    }

    @Test
    void test_hashCode() {
        WorkloadPriority p1 = WorkloadPriority.of(1, 2);
        WorkloadPriority p2 = WorkloadPriority.of(1, 2);
        assertNotSame(p1, p2);
        Map<WorkloadPriority, Integer> map = new HashMap<>();
        map.put(p1, 1);
        if (map.containsKey(p2)) {
            map.put(p2, map.get(p2) + 1);
        }
        assertEquals(1, map.size());
    }

}
