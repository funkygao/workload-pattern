package io.github.workload.overloading;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JvmTest {

    @Test
    void basic() {
        assertEquals(1L, JVM.getLong("a.b", 1));
        System.setProperty("a.b", "9");
        assertEquals(9L, JVM.getLong("a.b", 123L));

        assertEquals(0.12d, JVM.getDouble("workload.FO_B", 0.12d));
        System.setProperty("workload.FO_B", "0.28");
        assertEquals(0.28d, JVM.getDouble("workload.FO_B", 1.23d));

        assertThrows(NumberFormatException.class, () -> {
            System.setProperty("foo", "bar");
            JVM.getDouble("foo", 1.2d);
        });
    }

}