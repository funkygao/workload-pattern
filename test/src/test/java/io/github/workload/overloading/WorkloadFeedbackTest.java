package io.github.workload.overloading;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WorkloadFeedbackTest {

    @Test
    void factory() {
        WorkloadFeedback overloaded = WorkloadFeedback.ofOverloaded();
        assertTrue(overloaded instanceof WorkloadFeedbackOverloaded);
        WorkloadFeedbackOverloaded overloaded1 = (WorkloadFeedbackOverloaded) overloaded;
        assertNotEquals(0, overloaded1.getOverloadedAtNs());

        WorkloadFeedback queued = WorkloadFeedback.ofQueuedNs(300);
        WorkloadFeedbackQueued queued1 = (WorkloadFeedbackQueued) queued;
        assertEquals(300, queued1.getQueuedNs());
    }

}