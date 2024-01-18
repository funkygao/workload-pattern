package io.github.workload.overloading.bufferbloat.aqm;

import io.github.workload.annotations.Heuristics;
import io.github.workload.annotations.WIP;
import io.github.workload.metrics.smoother.ExponentialMovingAverage;
import io.github.workload.metrics.smoother.ValueSmoother;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.Random;

/**
 * Random Early Detection Algorithm：是一种无分类qdisc(queueing discipline，流量控制器).
 *
 * <p>主要是为了解决TCP Global Synchronization而产生的算法.</p>
 *
 * <p>旨在：队列平均长度保持在较低值.</p>
 * <p>队列满载视角：队列长度.</p>
 */
@WIP
class REDQueue implements QueueDiscipline {
    @Heuristics
    private int maxQueueSize; // 队列容纳数据包数量的最大值
    @Heuristics
    private double minThreshold; // 当平均队列大小超过此阈值时，开始计算丢包概率
    @Heuristics
    private double maxThreshold; // 当平均队列大小超过此阈值时，开始强制丢包
    @Heuristics
    private double maxProbability; // 计算的丢包概率不会超过此值

    private LinkedList<Packet> queue = new LinkedList<>();
    private double avgQueueSize = 0;
    private final Random random = new Random();
    // 这允许一定的burst
    private final ValueSmoother valueSmoother = new ExponentialMovingAverage(0.1);

    @Override
    public void enqueue(Packet packet) {
        if (queue.size() >= maxQueueSize) {
            // 队列满了，强制丢包：tail drop
            throw new QueueException();
        }

        if (minThreshold < avgQueueSize && avgQueueSize < maxThreshold) {
            //   accept              maybe              drop
            // 0 ------ minThreshold ----- maxThreshold ---- ∞
            // 进入拥塞阈值区间，开始按照概率进行丢弃
            if (random.nextDouble() < dropProbability()) {
                throw new QueueException();
            }
        } else if (avgQueueSize >= maxThreshold) {
            // 强制丢包
            throw new QueueException();
        }

        // accept
        queue.offer(packet);
        avgQueueSize = valueSmoother.update(queue.size()).smoothedValue();
    }

    @Override
    public Packet dequeue() {
        if (queue.isEmpty()) {
            return null;
        }

        Packet packet = queue.poll();
        avgQueueSize = valueSmoother.update(queue.size()).smoothedValue();
        return packet;
    }

    private double dropProbability() {
        if (minThreshold < avgQueueSize && avgQueueSize < maxThreshold) {
            // 在拥塞阈值区间
            final double thresholdWidth = maxThreshold - minThreshold;
            return maxProbability * (avgQueueSize - minThreshold) / thresholdWidth;
        }
        return 0;
    }

    @Test
    @Disabled
    void demo() {
        final Logger log = LoggerFactory.getLogger("REDQueue");

        REDQueue queue = new REDQueue();
        queue.maxQueueSize = 100;
        // 队列拥塞阈值区间：[60, 90]
        queue.minThreshold = 60;
        queue.maxThreshold = 90;
        queue.maxProbability = 0.6;

        for (int i = 0; i < 90; i++) {
            try {
                queue.enqueue(new Packet(i));
            } catch (QueueException e) {
                log.info("packet:{} dropped", i);
            }
        }
        log.info("dropProbability: {}", queue.dropProbability());
        for (int i = 0; i < 5; i++) {
            log.info("got packet:{}", queue.dequeue());
        }
    }

}
