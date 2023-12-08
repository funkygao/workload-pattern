package io.github.workload.overloading;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.HashSet;
import java.util.Set;

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
    void timeRandomU(TestInfo testInfo) throws InterruptedException {
        String uid = "34_2323";
        for (int i = 0; i < N; i++) {
            WorkloadPriority priority = WorkloadPriority.timeRandomU(1, uid.hashCode(), 5);
            Thread.sleep(3);
            //System.out.printf("T:%d %s %s\n", Thread.currentThread().getId(), priority, testInfo.getDisplayName());
            uniqueU.add(priority.U());
        }
    }
}
