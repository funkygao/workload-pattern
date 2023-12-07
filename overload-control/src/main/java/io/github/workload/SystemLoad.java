package io.github.workload;

import com.sun.management.OperatingSystemMXBean;
import lombok.extern.slf4j.Slf4j;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 系统负载探测器.
 *
 * <p>Borrowed from alibaba/Sentinel SystemStatusListener.java</p>
 * <p>解决了JDK10-下docker容器环境问题.</p>
 * <ul>Issues:
 * <li>JDK8u131之前版本，docker下获取的CPU核数不正确：返回的是宿主机的核数</li>
 * <li>如果docker中运行了两个java程序，那么每个进程只能统计自己占用的cpu而不知道整个系统处于何种状态</li>
 * <li>最终算出的cpu利用率取了宿主机cpu利用率和当前进程算出的cpu利用率的较大值，在docker的cpu被限制或者被绑定时，即cpu资源被隔离时，这两个值可能会相差很大，这时也并不太需要关注宿主机的cpu利用率</li>
 * </ul>
 *
 * @see <a href="https://cloud.tencent.com/developer/article/1760923">Sentinel在docker中获取CPU利用率的一个BUG</a>
 */
@Slf4j
public class SystemLoad {
    private static SystemLoad singleton = new SystemLoad();

    private double currentLoadAverage = -1;
    private double currentCpuUsage = -1;

    private long processCpuTimeNs = 0; // 当前进程累计占用CPU时长
    private long processUpTimeMs = 0; // 当前进程累计运行时长

    private SystemLoad() {
        ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory(SystemLoad.class.getSimpleName()));
        timer.scheduleAtFixedRate(() -> {
            compute();
        }, 2, 1, TimeUnit.SECONDS);
    }

    public static double loadAverage() {
        return singleton.currentLoadAverage;
    }

    /**
     * 最近的CPU利用率.
     *
     * <p>是指程序的CPU占用时间除以程序的运行时间</p>
     */
    public static double cpuUsage() {
        return singleton.currentCpuUsage;
    }

    private void compute() {
        OperatingSystemMXBean osBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
        // 操作系统的load avg，与cpu核数相关
        currentLoadAverage = osBean.getSystemLoadAverage();

        // normalized cpu load: [0.0,1.0], percentage
        double systemCpuUsage = osBean.getSystemCpuLoad();

        // calculate process cpu usage to support application running in container environment
        // 计算出每次JVM的运行时间差值与占用cpu的时间差值
        // 利用cpu占用时间差值除以JVM运行时间差值，再除以cpu的核数，计算出归一化后的cpu利用率
        // 每次都计算差值是为了取到比较精确的“瞬时”cpu利用率，而不是一个历史平均值
        RuntimeMXBean runtimeBean = ManagementFactory.getPlatformMXBean(RuntimeMXBean.class);
        long newProcessCpuTime = osBean.getProcessCpuTime();
        long newProcessUpTime = runtimeBean.getUptime();

        // 准确获取docker分配的cpu核数是从JDK8u131版本开始，之前都会返回宿主机的核数
        int cpuCores = osBean.getAvailableProcessors();

        long processCpuTimeDiffInMs = TimeUnit.NANOSECONDS
                .toMillis(newProcessCpuTime - processCpuTimeNs);
        long processUpTimeDiffInMs = newProcessUpTime - processUpTimeMs;
        double processCpuUsage = (double) processCpuTimeDiffInMs / processUpTimeDiffInMs / cpuCores;
        processCpuTimeNs = newProcessCpuTime;
        processUpTimeMs = newProcessUpTime;
        currentCpuUsage = Math.max(processCpuUsage, systemCpuUsage);

        log.debug("cpuCores:{}, getSystemLoadAverage:{}, getSystemCpuLoad:{}, getProcessCpuTime:{}ms, processUpTime:{}ms",
                cpuCores,
                currentLoadAverage,
                systemCpuUsage,
                processCpuTimeNs / 1000_000, // ns -> ms
                processUpTimeMs);
    }
}
