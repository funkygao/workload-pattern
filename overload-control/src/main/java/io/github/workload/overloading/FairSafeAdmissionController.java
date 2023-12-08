package io.github.workload.overloading;

import io.github.workload.annotations.ThreadSafe;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 面向QoS的自适应式工作负载准入管制，可用于RPC/异步任务排队/MQ消费等场景.
 *
 * <ul>About the naming:
 * <li>fair: based on {@link WorkloadPriority}, i,e. QoS</li>
 * <li>safe: embedded JVM scope CPU overload shedding mechanism</li>
 * </ul>
 */
@Slf4j
@ThreadSafe
class FairSafeAdmissionController implements AdmissionController {
    final WorkloadShedderOnQueue workloadShedderOnQueue;

    // shared singleton in JVM
    private static final WorkloadShedderOnCpu workloadShedderOnCpu = new WorkloadShedderOnCpu(CPU_USAGE_UPPER_BOUND);

    private static final ConcurrentHashMap<String, FairSafeAdmissionController> instances = new ConcurrentHashMap<>(8);

    private FairSafeAdmissionController() {
        this.workloadShedderOnQueue = new WorkloadShedderOnQueue();
    }

    static AdmissionController getInstance(@NonNull String kind) {
        // https://github.com/apache/shardingsphere/pull/13275/files
        // https://bugs.openjdk.org/browse/JDK-8161372
        AdmissionController instance = instances.get(kind);
        if (instance != null) {
            return instance;
        }

        return instances.computeIfAbsent(kind, key -> {
            log.info("register new kind: {}", kind);
            return new FairSafeAdmissionController();
        });
    }

    @Override
    public boolean admit(@NonNull WorkloadPriority workloadPriority) {
        // 进程级准入，全局采样
        if (!workloadShedderOnCpu.admit(workloadPriority)) {
            log.debug("CPU overloaded, might reject {}", workloadPriority);
            return false;
        }

        // 具体类型的业务准入，局部采样
        return workloadShedderOnQueue.admit(workloadPriority);
    }

    @Override
    public void recordQueuedNs(long queuedNs) {
        workloadShedderOnQueue.addWaitingNs(queuedNs);
    }

    @Override
    public void overloaded() {
        workloadShedderOnQueue.setOverloadedAtNs(System.nanoTime());
    }

}
