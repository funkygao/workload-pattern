package io.github.workload.overloading;

import io.github.workload.WorkloadPriority;
import io.github.workload.annotations.Experimental;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于FQ-CoDel算法的工作负载准入控制器.
 */
@Slf4j
@Experimental
public class FqCodelAdmissionController implements AdmissionController {
    private final String name;

    private static final Map<String, FqCodelAdmissionController> instances = new ConcurrentHashMap<>(8);

    private FqCodelAdmissionController(String name) {
        this.name = name;
    }

    static AdmissionController getInstance(@NonNull String name) {
        AdmissionController instance = instances.get(name);
        if (instance != null) {
            return instance;
        }

        return instances.computeIfAbsent(name, key -> {
            log.info("register new admission controller:{}", name);
            return new FqCodelAdmissionController(name);
        });
    }

    @Override
    public boolean admit(@NonNull WorkloadPriority priority) {
        return false;
    }

    @Override
    public void feedback(@NonNull WorkloadFeedback feedback) {

    }
}
