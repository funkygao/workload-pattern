package io.github.workload.overloading;

import io.github.workload.WorkloadPriority;
import io.github.workload.annotations.VisibleForTesting;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 面向QoS的自适应式工作负载准入管制，可用于RPC/异步任务排队/MQ消费等场景.
 *
 * <ul>About the naming:
 * <li>fair: based on {@link WorkloadPriority}, i,e. QoS</li>
 * <li>safe: embedded JVM scope CPU overload shedding mechanism</li>
 * </ul>
 * <ol>Load shedding principles:
 * <li>Sustain peak performance</li>
 * <li>Isolation: misbehaving customers never hurt anyone(but themselves)</li>
 * <li>Prioritize requests clearly</li>
 * <li>Customer quotas only enforced when needed</li>
 * <li>Cost based: Don't model capacity with QPS</li>
 * <li>Retries: per-request max attempts(e,g. 3), per-backend retry budget(e,g. 10% of all requests)</li>
 * </ol>
 * <ul>局限性，无法解决这类问题:
 * <li>某个请求(canary request)导致CPU瞬间从低暴涨到100%：shuffle sharding可以
 *   <ul>
 *     <li>crash，例如：某个bug导致死循环/StackOverflow，而该bug在某种请求条件下才触发</li>
 *     <li>high priority workload long delay，例如：误把{@link ConcurrentHashMap#contains(Object)}当做O(1)，配合parallelStream，当数据量多时CPU彪高</li>
 *   </ul>
 * </li>
 * <li>只卸除新请求，已接受的请求(已经在执行，在{@link BlockingQueue}里等待执行)即使耗尽CPU也无法卸除</li>
 * <li>请求的优先级分布完全没有规律，且完全集中。例如，当前窗口内全部是优先级为A的请求，到下一个窗口全部是B的请求</li>
 * </ul>
 */
@Slf4j
class DAGORAdmissionController implements AdmissionController {
    /**
     * The pessimistic throttling.
     */
    private final WorkloadShedderOnQueue shedderOnQueue;

    /**
     * The optimistic throttling.
     *
     * <p>shared singleton in JVM: not shed load until you reach global capacity.</p>
     * <p>The downside of optimistic throttling, is that you'll spike over your global maximum while you start shedding load.</p>
     * <p>Most users will only experience this momentary overload in the form of slightly higher latency.</p>
     */
    private static final WorkloadShedderOnCpu shedderOnCpu = new WorkloadShedderOnCpu(CPU_USAGE_UPPER_BOUND, CPU_OVERLOAD_COOL_OFF_SEC);

    DAGORAdmissionController(String name) {
        this.shedderOnQueue = new WorkloadShedderOnQueue(name);
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

}
