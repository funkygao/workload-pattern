package io.github.workload.overloading;

import io.github.workload.Workload;
import io.github.workload.WorkloadPriority;
import io.github.workload.annotations.VisibleForTesting;
import io.github.workload.overloading.metrics.IMetricsTracker;
import io.github.workload.overloading.metrics.IMetricsTrackerFactory;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 面向QoS的自适应式工作负载准入管制，可用于RPC/异步任务排队/MQ消费等场景.
 * 集成了基于(队列delay，优先级/QoS，CPU饱和)的基于<a href="https://arxiv.org/abs/1806.04075">Overload Control for Scaling WeChat Microservices</a>的准入控制器实现.
 *
 * <ul>About the naming:
 * <li>fair: based on {@link WorkloadPriority}, i,e. QoS</li>
 * <li>safe: embedded JVM scope CPU overload shedding mechanism</li>
 * </ul>
 * <ul>局限性，无法解决这类问题:
 * <li>请求的优先级分布完全没有规律，且完全集中。例如，当前窗口内全部是优先级为A的请求，到下一个窗口全部是B的请求
 *   <ul>
 *       <li>上层应用可以设定<a href="https://www.envoyproxy.io/docs/envoy/latest/intro/arch_overview/upstream/load_balancing/panic_threshold.html">envoy panic threshold</a>那样的shed阈值做保护</li>
 *   </ul>
 * </li>
 * <li>某个请求(canary request)导致CPU瞬间从低暴涨到100%：shuffle sharding可以
 *   <ul>
 *     <li>crash，例如：某个bug导致死循环/StackOverflow，而该bug在某种请求条件下才触发</li>
 *     <li>high priority workload long delay，例如：误把{@link ConcurrentHashMap#contains(Object)}当做O(1)，配合parallelStream，当数据量多时CPU彪高</li>
 *   </ul>
 * </li>
 * <li>只卸除新请求，已接受的请求(已经在执行，在{@link BlockingQueue}里等待执行)即使耗尽CPU也无法卸除</li>
 * </ul>
 */
@Slf4j
class FairSafeAdmissionController implements AdmissionController {
    static final long CPU_OVERLOAD_COOL_OFF_SEC = JVM.getLong(JVM.CPU_OVERLOAD_COOL_OFF_SEC, 10 * 60);
    static final double CPU_USAGE_UPPER_BOUND = JVM.getDouble(JVM.CPU_USAGE_UPPER_BOUND, 0.75);

    /**
     * The optimistic throttling.
     *
     * <p>Shared singleton in JVM: not shed load until you reach global capacity.</p>
     * <p>The downside of optimistic throttling is that you'll spike over your global maximum while you start shedding load.</p>
     * <p>Most users will only experience this momentary overload in the form of slightly higher latency.</p>
     */
    private static final WorkloadShedderOnCpu shedderOnCpu = new WorkloadShedderOnCpu(CPU_USAGE_UPPER_BOUND, CPU_OVERLOAD_COOL_OFF_SEC);

    /**
     * The pessimistic throttling.
     */
    private final WorkloadShedderOnQueue shedderOnQueue;

    private final IMetricsTracker metricsTracker;

    FairSafeAdmissionController(String name) {
        this(name, null);
    }

    FairSafeAdmissionController(String name, IMetricsTrackerFactory metricsTrackerFactory) {
        this.shedderOnQueue = new WorkloadShedderOnQueue(name);
        if (metricsTrackerFactory == null) {
            this.metricsTracker = new NopMetricsTracker();
        } else {
            this.metricsTracker = metricsTrackerFactory.create(name);
        }
    }

    @Override
    public boolean admit(@NonNull Workload workload) {
        final WorkloadPriority priority = workload.getPriority();
        metricsTracker.enter(workload.getPriority());
        if (!shedderOnCpu.admit(priority)) {
            metricsTracker.shedByCpu(priority);
            log.warn("{}:shared CPU saturated, shed {} above watermark {}", shedderOnQueue.name, priority.simpleString(), shedderOnCpu.admissionLevel().simpleString());
            return false;
        }

        // 具体类型的业务准入，局部采样
        boolean ok = shedderOnQueue.admit(priority);
        if (!ok) {
            metricsTracker.shedByQueue(priority);
            log.warn("{}:queuing busy, shed {} above watermark {}", shedderOnQueue.name, priority.simpleString(), shedderOnQueue.admissionLevel().simpleString());
        }
        return ok;
    }

    @Override
    public void feedback(@NonNull WorkloadFeedback feedback) {
        if (feedback instanceof WorkloadFeedback.Overload) {
            shedderOnQueue.overload(((WorkloadFeedback.Overload) feedback).getOverloadAtNs());
            return;
        }

        if (feedback instanceof WorkloadFeedback.Queued) {
            shedderOnQueue.addWaitingNs(((WorkloadFeedback.Queued) feedback).getQueuedNs());
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

    private static class NopMetricsTracker implements IMetricsTracker {

    }
}
