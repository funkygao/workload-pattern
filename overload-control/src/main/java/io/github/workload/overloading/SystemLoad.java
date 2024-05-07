package io.github.workload.overloading;

import com.sun.management.OperatingSystemMXBean;
import io.github.workload.NamedThreadFactory;
import io.github.workload.SystemLoadProvider;
import lombok.extern.slf4j.Slf4j;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 系统负载探测器.
 *
 * <p>In platforms with garbage collection, memory pressure naturally translates into increased CPU consumption.</p>
 * <p>Borrowed from alibaba/Sentinel SystemStatusListener.java</p>
 * <p>解决了JDK10-下docker容器环境问题.</p>
 * <ul>Issues:
 * <li>JDK8u131之前版本，docker下获取的CPU核数不正确：返回的是宿主机的核数；目前使用的版本都大于此版本</li>
 * <li>如果docker中运行了两个java程序，那么每个进程只能统计自己占用的cpu而不知道整个系统处于何种状态</li>
 * <li>最终算出的cpu利用率取了宿主机cpu利用率和当前进程算出的cpu利用率的较大值，在docker的cpu被限制或者被绑定时，即cpu资源被隔离时，这两个值可能会相差很大，这时也并不太需要关注宿主机的cpu利用率</li>
 * </ul>
 *
 * @see <a href="https://cloud.tencent.com/developer/article/1760923">Sentinel在docker中获取CPU利用率的一个BUG</a>
 */
@Slf4j
class SystemLoad implements SystemLoadProvider {
    private volatile double currentLoadAverage = -1;
    private volatile double currentCpuUsage = -1;

    private long processCpuTimeNs = 0; // 当前进程累计占用CPU时长
    private long processUpTimeMs = 0; // 当前进程累计运行时长

    static SystemLoad getInstance(long coolOffSec) {
        return new SystemLoad(coolOffSec);
    }

    private SystemLoad(long coolOffSec) {
        ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory(SystemLoad.class.getSimpleName()));
        // 冷静期后才开始刷新数据：JVM启动时CPU往往很高
        timer.scheduleAtFixedRate(this::safeRefresh, coolOffSec, 1, TimeUnit.SECONDS);
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                timer.shutdown();
                if (!timer.awaitTermination(3, TimeUnit.SECONDS)) {
                    timer.shutdownNow();
                }
            } catch (InterruptedException why) {
                Thread.currentThread().interrupt();
                log.error("Interrupted during shutdown", why);
            } catch (Exception why) {
                log.error("Error during executor shutdown", why);
            }
        }));
    }

    @Override
    public double cpuUsage() {
        return currentCpuUsage;
    }

    // ScheduledExecutorService的实现通常不会对抛异常的任务进行重新调度
    private void safeRefresh() {
        try {
            refresh();
        } catch (Exception why) {
            log.error("Refresh of CPU utilization failed", why);
        }
    }

    private void refresh() {
        OperatingSystemMXBean osBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
        currentLoadAverage = osBean.getSystemLoadAverage();
        if (currentLoadAverage < 0) {
            log.warn("JVM getSystemLoadAverage got:{}, currentLoadAverage reset to 0", currentLoadAverage);
            currentLoadAverage = 0;
        }

        // normalized cpu load: [0.0,1.0], percentage
        final double newSystemCpuUsage = osBean.getSystemCpuLoad();
        if (Double.isNaN(newSystemCpuUsage)) {
            // 某些情况下可能会返回 NaN，这取决于JVM的实现和运行时环境
            log.warn("JVM getSystemCpuLoad got NaN, skip this cycle");
            return;
        }

        // calculate process cpu usage to support application running in container environment
        // 计算出每次JVM的运行时间差值与占用cpu的时间差值
        // 利用cpu占用时间差值除以JVM运行时间差值，再除以cpu的核数，计算出归一化后的cpu利用率
        // 每次都计算差值是为了取到比较精确的“瞬时”cpu利用率，而不是一个历史平均值
        final RuntimeMXBean runtimeBean = ManagementFactory.getPlatformMXBean(RuntimeMXBean.class);
        final long newProcessCpuTime = osBean.getProcessCpuTime();
        final long newProcessUpTime = runtimeBean.getUptime();

        // 准确获取docker分配的cpu核数是从JDK8u131版本开始，之前都会返回宿主机的核数
        final int cpuCores = osBean.getAvailableProcessors();

        final long processCpuTimeDiffInMs = TimeUnit.NANOSECONDS
                .toMillis(newProcessCpuTime - processCpuTimeNs);
        final long processUpTimeDiffInMs = newProcessUpTime - processUpTimeMs;
        if (processUpTimeDiffInMs > 0) {
            // it should never be 0, but for safety, we check it
            final double processCpuUsage = (double) processCpuTimeDiffInMs / processUpTimeDiffInMs / cpuCores;
            currentCpuUsage = Math.max(processCpuUsage, newSystemCpuUsage);
        } else {
            log.warn("processUpTimeDiffInMs is 0, newProcessUpTime:{}, force cpu usage:{}", newProcessUpTime, newSystemCpuUsage);
            currentCpuUsage = newSystemCpuUsage;
        }

        processCpuTimeNs = newProcessCpuTime;
        processUpTimeMs = newProcessUpTime;

        log.debug("cpuUsage:{}, loadAvg:{}, cpuCores:{} processUpTime:{}ms", currentCpuUsage, currentLoadAverage, cpuCores, processUpTimeMs);
    }
}
