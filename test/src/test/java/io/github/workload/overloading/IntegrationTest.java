package io.github.workload.overloading;

import io.github.workload.Workload;
import io.github.workload.WorkloadPriority;
import io.github.workload.simulate.WorkloadPrioritizer;
import org.junit.jupiter.api.Test;

class IntegrationTest {

    void usageDemo() {
        AdmissionController ac = AdmissionController.getInstance("HTTP");
        // in servlet filter
        long requestReceived = System.nanoTime();
        WorkloadPriority priority = WorkloadPrioritizer.randomWeb();
        boolean ok = ac.admit(Workload.ofPriority(priority));
        ac.feedback(AdmissionController.Feedback.ofQueuedNs(System.nanoTime() - requestReceived));
        if (!ok) {
            // reject
        }
    }

    @Test
    void basic() {

    }

}
