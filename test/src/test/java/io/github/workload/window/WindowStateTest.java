package io.github.workload.window;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class WindowStateTest {

    @Test
    void test_equals() {
        WindowState state1 = new WindowState(1);
        WindowState state2 = new WindowState(1);
        assertTrue(state1.equals(state2));
    }

}