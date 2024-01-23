package io.github.workload.overloading.bufferbloat.aqm;

import io.github.workload.annotations.Heuristics;
import io.github.workload.annotations.WIP;
import io.github.workload.metrics.smoother.ValueSmoother;
import lombok.AllArgsConstructor;
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
 * <p>enque时决定reject：启发性参数(队列长度处于拥塞的大小区间，允许的最大丢包率) + 移动平均的队列长度</p>
 */
@WIP
class REDQueue implements QueueDiscipline {
    @Heuristics
    private int maxQueueSize; // 队列容纳数据包数量的最大值
    private CongestionQueueSizeRange congestionRange;
    @Heuristics
    private double maxDropProbability; // 计算的丢包概率不会超过此值

    private LinkedList<Packet> queue = new LinkedList<>();
    private double avgQueueSize = 0;
    private final Random random = new Random();
    // 这允许一定的burst
    private final ValueSmoother valueSmoother = ValueSmoother.ofEMA(0.1);

    @Override
    public void enqueue(Packet packet) {
        if (queue.size() >= maxQueueSize) {
            // 队列满了，强制丢包：tail drop
            throw new QueueException();
        }

        double dropProbability = congestionRange.dropProbability(avgQueueSize, maxDropProbability);
        if (dropProbability >= 1) {
            // 强制丢包
            throw new QueueException();
        }

        if (random.nextDouble() < dropProbability) {
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

    @AllArgsConstructor
    private static class CongestionQueueSizeRange {
        @Heuristics
        private double minThreshold; // 当平均队列大小超过此阈值时，开始计算丢包概率
        @Heuristics
        private double maxThreshold; // 当平均队列大小超过此阈值时，开始强制丢包

        public double dropProbability(double avgQueueSize, double maxDropProbability) {
            if (avgQueueSize > maxThreshold) {
                return 1; // 100%
            }
            if (avgQueueSize < minThreshold) {
                return 0;
            }

            //   accept              maybe              drop
            // 0 ------ minThreshold ----- maxThreshold ---- ∞
            final double totalWidth = maxThreshold - minThreshold;
            final double advancedWidth = avgQueueSize - minThreshold;
            return maxDropProbability * advancedWidth / totalWidth;
        }
    }

    @Test
    @Disabled
    void demo() {
        final Logger log = LoggerFactory.getLogger("REDQueue");

        REDQueue queue = new REDQueue();
        // 队列拥塞阈值区间：[60, 90]
        queue.congestionRange = new CongestionQueueSizeRange(60, 90);
        queue.maxQueueSize = 100;
        queue.maxDropProbability = 0.6;

        for (int i = 0; i < 90; i++) {
            try {
                queue.enqueue(new Packet(i));
            } catch (QueueException e) {
                log.info("packet:{} dropped", i);
            }
        }
        for (int i = 0; i < 5; i++) {
            log.info("got packet:{}", queue.dequeue());
        }
    }

}
