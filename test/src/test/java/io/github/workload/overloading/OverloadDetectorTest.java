package io.github.workload.overloading;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ConcurrentSkipListMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OverloadDetectorTest {

    @Test
    void ConcurrentSkipListMap() {
        ConcurrentSkipListMap<Integer, String> map = new ConcurrentSkipListMap();
        for (int i = 0; i < 10; i++) {
            map.put(i, "str");
        }

        assertEquals("[0, 1, 2, 3]", map.headMap(3, true).keySet().toString());
        assertEquals("[3, 2, 1, 0]", map.headMap(3, true).descendingKeySet().toString());
        assertEquals("[0, 1, 2]", map.headMap(3).keySet().toString());
        assertEquals("[5, 6, 7, 8, 9]", map.tailMap(5).keySet().toString());
    }

    @Test
    void nanoTime() {
        long lastNs = System.nanoTime();
        for (int i = 0; i < 100; i++) {
            long nowNs = System.nanoTime();
            System.out.println((nowNs - lastNs) / 1000); // us
            lastNs = nowNs;
        }
    }

    @Test
    @RepeatedTest(value = 50)
    void basic() {
        OverloadDetector detector = new OverloadDetector(1);
        assertTrue(detector.admit(null));
    }

}
