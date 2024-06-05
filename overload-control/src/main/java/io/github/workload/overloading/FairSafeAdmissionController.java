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
 * 面向QoS的自适应式工作负荷准入管制，可用于RPC/异步任务排队/MQ消费等场景.
 *
 * <p>自适应地调节准入门槛，避免持续过载.</p>
 * <p>集成了(队列delay，优先级/QoS，CPU饱和)的基于<a href="https://arxiv.org/abs/1806.04075">Overload Control for Scaling WeChat Microservices</a>的准入控制器实现.</p>
 *
 * <ul>vs Netflix(Gradient/Vegas) algorithm, AQM(CoDel)
 * <ul>相同
 *     <li>反馈控制</li>
 *     <li>自适应性</li>
 *     <li>目标导向</li>
 *     <li>平滑处理</li>
 * </ul>
 * <ul>不同
 *     <li>可以应用于所有类型{@link Workload}：RPC/HTTP/MQ/Task/etc</li>
 *     <li>基于({@link WorkloadPriority}, (cpu, queuingTime)) vs RTT</li>
 *     <li>全局视角：{@link WorkloadPriority}在微服务上下游之间继承式传递，流量入口确定{@link WorkloadPriority}</li>
 *     <li>引入过载梯度，PID控制</li>
 * </ul>
 * </ul>
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
 * <li>上线了毒代码，canary request 导致CPU瞬间从低暴涨到100%：shuffle sharding可以
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
    private static final FairShedderCpu fairCpu = new FairShedderCpu();
    private final FairShedderQueue fairQueue;
    private final IMetricsTracker metricsTracker;

    FairSafeAdmissionController(String name) {
        this(name, null);
    }

    FairSafeAdmissionController(String name, IMetricsTrackerFactory metricsTrackerFactory) {
        this.fairQueue = new FairShedderQueue(name);
        this.metricsTracker = metricsTrackerFactory != null ? metricsTrackerFactory.create(name) : new NopMetricsTracker();
    }

    @Override
    public boolean admit(@NonNull Workload workload) {
        final WorkloadPriority priority = workload.getPriority();
        metricsTracker.enter(workload.getPriority());

        // JVM范围内共享的CPU准入机制：GC内存压力会隐式表现为CPU压力
        if (!fairCpu.admit(priority)) {
            log.info("[{}] busy CPU shed workload:{}, watermark:{}", fairQueue.name, priority.simpleString(), fairCpu.watermark().simpleString());
            metricsTracker.shedByCpu(priority);
            return false;
        }

        // 基于单独队列的准入机制
        boolean ok = fairQueue.admit(priority);
        if (!ok) {
            log.info("[{}] busy Queue shed workload:{}, watermark:{}", fairQueue.name, priority.simpleString(), fairQueue.watermark().simpleString());
            metricsTracker.shedByQueue(priority);
        }
        return ok;
    }

    @Override
    public void feedback(@NonNull AdmissionController.Feedback feedback) {
        if (feedback instanceof Feedback.Overload) {
            fairQueue.overload(((Feedback.Overload) feedback).getOverloadAtNs());
            return;
        }

        if (feedback instanceof Feedback.Queued) {
            fairQueue.addWaitingNs(((Feedback.Queued) feedback).getQueuedNs());
        }
    }

    @VisibleForTesting
    FairShedderQueue fairQueue() {
        return fairQueue;
    }

    @VisibleForTesting
    static FairShedderCpu fairCpu() {
        return fairCpu;
    }

    private static class NopMetricsTracker implements IMetricsTracker {

    }
}
