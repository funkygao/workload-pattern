package io.github.workload.overloading.bufferbloat;

import io.github.workload.BaseConcurrentTest;
import io.github.workload.annotations.Heuristics;
import io.github.workload.metrics.smoother.ExponentialMovingAverage;
import io.github.workload.metrics.smoother.ValueSmoother;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.LinkedList;
import java.util.Random;

// Random Early Detection Algorithm
class REDQueue extends BaseConcurrentTest {
    @Heuristics
    private int maxQueueSize; // 队列容纳数据包数量的最大值
    @Heuristics
    private double minThreshold; // 当平均队列大小超过此阈值时，开始计算丢包概率
    @Heuristics
    private double maxThreshold; // 当平均队列大小超过此阈值时，开始强制丢包
    @Heuristics
    private double maxProbability; // 计算的丢包概率不会超过此值

    private LinkedList<Integer> queue = new LinkedList<>();
    private double avgQueueSize = 0;
    private final Random random = new Random();
    private final ValueSmoother valueSmoother = new ExponentialMovingAverage(0.1);

    public boolean enqueue(int packet) {
        if (queue.size() >= maxQueueSize) {
            // 队列满了，强制丢包
            return false;
        }

        if (minThreshold < avgQueueSize && avgQueueSize < maxThreshold) {
            //   accept              maybe              drop
            // 0 ------ minThreshold ----- maxThreshold ---- ∞
            // 进入拥塞阈值区间，开始按照概率进行丢弃
            if (random.nextDouble() < dropProbability()) {
                return false;
            }
        } else if (avgQueueSize >= maxThreshold) {
            // 强制丢包
            return false;
        }

        // accept
        queue.offer(packet);
        avgQueueSize = valueSmoother.update(queue.size()).smoothedValue();
        return true;
    }

    public Integer dequeue() {
        if (queue.isEmpty()) {
            return null;
        }

        Integer packet = queue.poll();
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
        REDQueue queue = new REDQueue();
        queue.maxQueueSize = 100;
        // 队列拥塞阈值区间：[60, 90]
        queue.minThreshold = 60;
        queue.maxThreshold = 90;
        queue.maxProbability = 0.6;

        for (int i = 0; i < 90; i++) {
            if (!queue.enqueue(i)) {
                log.info("packet:{} dropped", i);
            }
        }
        log.info("dropProbability: {}", queue.dropProbability());
        for (int i = 0; i < 5; i++) {
            log.info("got packet:{}", queue.dequeue());
        }
    }

}
