package io.github.workload;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HyperParameterTest {

    @Test
    void basic() {
        assertEquals(1L, HyperParameter.getLong("a.b", 1));
        System.setProperty("a.b", "9");
        assertEquals(9L, HyperParameter.getLong("a.b", 123L));
        assertEquals(9, HyperParameter.getInt("a.b", 123));

        assertEquals(0.12d, HyperParameter.getDouble("workload.FO_B", 0.12d));
        System.setProperty("workload.FO_B", "0.28");
        assertEquals(0.28d, HyperParameter.getDouble("workload.FO_B", 1.23d));

        assertThrows(NumberFormatException.class, () -> {
            System.setProperty("foo", "bar");
            HyperParameter.getDouble("foo", 1.2d);
        });
    }

}