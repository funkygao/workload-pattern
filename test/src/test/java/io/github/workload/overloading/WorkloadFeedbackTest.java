package io.github.workload.overloading;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WorkloadFeedbackTest {

    @Test
    void factory() {
        WorkloadFeedback overloaded = WorkloadFeedback.ofOverloaded();
        assertTrue(overloaded instanceof WorkloadFeedback.Overload);
        WorkloadFeedback.Overload overloaded1 = (WorkloadFeedback.Overload) overloaded;
        assertNotEquals(0, overloaded1.getOverloadAtNs());

        WorkloadFeedback queued = WorkloadFeedback.ofQueuedNs(300);
        WorkloadFeedback.Queued queued1 = (WorkloadFeedback.Queued) queued;
        assertEquals(300, queued1.getQueuedNs());
    }

}