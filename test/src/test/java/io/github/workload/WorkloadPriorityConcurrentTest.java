package io.github.workload;

import io.github.workload.WorkloadPriority;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkloadPriorityConcurrentTest {

    static Set<Integer> uniqueU = new HashSet<>();
    static int N = 10;

    @BeforeAll
    static void reset() {
        uniqueU.clear();
    }

    @AfterAll
    static void validate() {
        assertTrue(N > uniqueU.size() && uniqueU.size() > 3);
    }

    @RepeatedTest(20)
    @Execution(ExecutionMode.CONCURRENT)
    void ofPeriodicRandomFromUID(TestInfo testInfo) throws InterruptedException {
        String uid = "34_2323";
        for (int i = 0; i < N; i++) {
            WorkloadPriority priority = WorkloadPriority.ofPeriodicRandomFromUID(1, uid.hashCode(), 5);
            assertEquals(1, priority.B());
            Thread.sleep(3);
            uniqueU.add(priority.U());
        }
    }
}
