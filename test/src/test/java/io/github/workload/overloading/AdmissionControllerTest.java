package io.github.workload.overloading;

import io.github.workload.Workload;
import io.github.workload.WorkloadPriority;
import io.github.workload.overloading.metrics.IMetricsTrackerFactory;
import io.github.workload.overloading.metrics.MicrometerMetricsTrackerFactory;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import static org.junit.jupiter.api.Assertions.*;

class AdmissionControllerTest {

    @RepeatedTest(10)
    @Execution(ExecutionMode.CONCURRENT)
    void getInstance() {
        AdmissionController controller = AdmissionController.getInstance("foo");
        assertTrue(controller instanceof FairSafeAdmissionController);
        assertSame(controller, AdmissionController.getInstance("foo"));
        assertNotSame(controller, AdmissionController.getInstance("bar"));

        assertThrows(NullPointerException.class, () -> {
            AdmissionController.getInstance(null);
        });
    }

    @Test
    void nonNull() {
        AdmissionController controller = AdmissionController.getInstance("test");
        assertThrows(NullPointerException.class, () -> {
            controller.admit(null);
        });

        assertThrows(NullPointerException.class, () -> {
            controller.feedback(null);
        });
    }

    @Test
    void metricsFactory_smokeTest() {
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        IMetricsTrackerFactory factory = new MicrometerMetricsTrackerFactory(meterRegistry);
        AdmissionController controller = AdmissionController.getInstance("test5", factory);
        WorkloadPriority priority = WorkloadPriority.fromP(10);
        controller.admit(Workload.ofPriority(priority));
    }

    @Test
    void feedback() {
        AdmissionController.Feedback overloaded = AdmissionController.Feedback.ofOverloaded();
        assertTrue(overloaded instanceof AdmissionController.Feedback.Overload);
        AdmissionController.Feedback.Overload overloaded1 = (AdmissionController.Feedback.Overload) overloaded;
        assertNotEquals(0, overloaded1.getOverloadAtNs());

        AdmissionController.Feedback queued = AdmissionController.Feedback.ofQueuedNs(300);
        AdmissionController.Feedback.Queued queued1 = (AdmissionController.Feedback.Queued) queued;
        assertEquals(300, queued1.getQueuedNs());
    }
}
