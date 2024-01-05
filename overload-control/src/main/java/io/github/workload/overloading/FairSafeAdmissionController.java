package io.github.workload.overloading;

import io.github.workload.annotations.ThreadSafe;
import io.github.workload.annotations.VisibleForTesting;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 面向QoS的自适应式工作负载准入管制，可用于RPC/异步任务排队/MQ消费等场景.
 *
 * <ul>About the naming:
 * <li>fair: based on {@link WorkloadPriority}, i,e. QoS</li>
 * <li>safe: embedded JVM scope CPU overload shedding mechanism</li>
 * </ul>
 * <ul>局限性，无法解决这类问题:
 * <li>某个请求(canary request)导致CPU瞬间从低暴涨到100%：shuffle sharding可以
 *   <ul>
 *     <li>crash，例如：某个bug导致死循环/StackOverflow，而该bug在某种请求条件下才触发</li>
 *     <li>long delay，例如：误把{@link ConcurrentHashMap#contains(Object)}当做O(1)，配合parallelStream，当数据量多时CPU彪高</li>
 *   </ul>
 * </li>
 * <li>只卸除新请求，已接受的请求(已经在执行，在{@link BlockingQueue}里等待执行)即使耗尽CPU也无法卸除</li>
 * </ul>
 */
@Slf4j
@ThreadSafe
class FairSafeAdmissionController implements AdmissionController {
    private final WorkloadShedderOnQueue shedderOnQueue;

    // shared singleton in JVM
    private static final WorkloadShedderOnCpu shedderOnCpu = new WorkloadShedderOnCpu(CPU_USAGE_UPPER_BOUND, CPU_OVERLOAD_COOL_OFF_SEC);

    private static final Map<String, FairSafeAdmissionController> instances = new ConcurrentHashMap<>(8);

    private FairSafeAdmissionController(String name) {
        this.shedderOnQueue = new WorkloadShedderOnQueue(name);
    }

    static AdmissionController getInstance(@NonNull String name) {
        // https://github.com/apache/shardingsphere/pull/13275/files
        // https://bugs.openjdk.org/browse/JDK-8161372
        AdmissionController instance = instances.get(name);
        if (instance != null) {
            return instance;
        }

        return instances.computeIfAbsent(name, key -> {
            log.info("register new admission controller:{}", name);
            return new FairSafeAdmissionController(name);
        });
    }

    @Override
    public boolean admit(@NonNull WorkloadPriority priority) {
        // 进程级准入，全局采样
        if (!shedderOnCpu.admit(priority)) {
            log.debug("CPU overloaded, might reject {}", priority);
            return false;
        }

        // 具体类型的业务准入，局部采样
        return shedderOnQueue.admit(priority);
    }

    @Override
    public void feedback(@NonNull WorkloadFeedback feedback) {
        if (feedback instanceof WorkloadFeedbackOverloaded) {
            shedderOnQueue.overload(((WorkloadFeedbackOverloaded) feedback).getOverloadedAtNs());
            return;
        }

        if (feedback instanceof WorkloadFeedbackQueued) {
            shedderOnQueue.addWaitingNs(((WorkloadFeedbackQueued) feedback).getQueuedNs());
        }
    }

    @VisibleForTesting
    WorkloadShedderOnQueue shedderOnQueue() {
        return shedderOnQueue;
    }

    @VisibleForTesting
    static WorkloadShedderOnCpu shedderOnCpu() {
        return shedderOnCpu;
    }

    @VisibleForTesting("清除共享的静态变量，以便隔离单元测试")
    static void resetForTesting() {
        instances.clear();
    }

}
