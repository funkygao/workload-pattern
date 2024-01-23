package io.github.workload.overloading;

import io.github.workload.WorkloadPriority;
import io.github.workload.annotations.Experimental;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * 基于FQ-CoDel算法的工作负载准入控制器.
 */
@Slf4j
@Experimental
public class FqCodelAdmissionController implements AdmissionController {
    private final String name;

    FqCodelAdmissionController(String name) {
        this.name = name;
    }

    @Override
    public boolean admit(@NonNull WorkloadPriority priority) {
        return false;
    }

    @Override
    public void feedback(@NonNull WorkloadFeedback feedback) {

    }
}
