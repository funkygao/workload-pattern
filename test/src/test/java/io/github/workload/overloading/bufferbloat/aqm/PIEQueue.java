package io.github.workload.overloading.bufferbloat.aqm;

import java.util.LinkedList;
import java.util.Queue;

/**
 * Proportional Integral controller Enhanced queue：加强的比例积分控制.
 *
 * <p>控制思路跟{@link CoDelQueue}一样，都是针对请求的延迟进行控制而不是队列长度，但是其对超时请求处理方法跟{@link REDQueue}一样，都是随机对数据包进行丢弃</p>
 * <p>严格来说，它是根据队列中请求延时时间的变化率(就是当前延时时间与目标延时时间的差值与时间的积分)来判断拥塞，目的是可以让算法本身在各种网络阻塞的情况下都能自动调节以优化性能表现</p>
 */
class PIEQueue implements QueueDiscipline {
    private Queue<Packet> queue;
    private double dropProbability;   // 当前丢包概率
    private long targetDelay;         // 目标延迟，以毫秒为单位
    private double accError;          // 积分项中的累积误差
    private long lastUpdateTime;      // 上次更新时间

    // PIE控制参数
    private final double alpha;       // 比例部分的系数
    private final double beta;        // 积分部分的系数
    private final long updateInterval;       // 更新丢包概率的时间间隔，以毫秒为单位

    // 构造方法初始化队列，并设置PIE算法相关的参数
    public PIEQueue(long targetDelay, double alpha, double beta, long updateInterval) {
        this.queue = new LinkedList<>();
        this.targetDelay = targetDelay;
        this.dropProbability = 0.0;
        this.accError = 0.0;
        this.alpha = alpha;
        this.beta = beta;
        this.updateInterval = updateInterval;
        this.lastUpdateTime = System.currentTimeMillis();
    }

    @Override
    public void enqueue(Packet packet) {
        long currentTime = System.currentTimeMillis();
        if (shouldUpdate(currentTime)) {
            updateDropProbability(currentTime);  // 根据当前时间更新丢包概率
        }

        // 基于丢包概率决定是否丢弃数据包
        if (Math.random() < dropProbability) {
            return;
        }

        packet.enqueue(currentTime);
        queue.add(packet);
    }

    @Override
    public Packet dequeue() {
        return queue.poll();
    }

    // 判断是否应更新丢包概率
    private boolean shouldUpdate(long currentTime) {
        return (currentTime - lastUpdateTime) >= updateInterval;
    }

    // 使用比例（P）和积分（I）控制的方法来动态更新丢包概率
    private void updateDropProbability(long currentTime) {
        // 计算自上次更新以来的时间，转换为秒
        double delta = (currentTime - lastUpdateTime) / 1000.0;
        // 获取当前队列的平均延迟（此处简化模型，假定等于队头数据包的延时）
        long queueDelay = !queue.isEmpty() ? currentTime - queue.peek().arrivalTime() : 0;
        // 计算延迟的差值
        double delayDiff = queueDelay - targetDelay;
        // 更新累积误差
        accError += delayDiff * delta;
        // 更新丢包概率
        dropProbability += alpha * delayDiff + beta * accError;
        // 保证丢包概率在0和1之间
        dropProbability = Math.max(0.0, Math.min(dropProbability, 1.0));
        // 更新上次更新时间为当前时间
        lastUpdateTime = currentTime;
    }
}
