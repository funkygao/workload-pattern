package io.github.workload.overloading;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ConcurrentSkipListMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorkloadShedderTest {

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

}
