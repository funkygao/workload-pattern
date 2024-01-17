package io.github.workload.overloading.bufferbloat.aqm;

import io.github.workload.annotations.Heuristics;

import java.util.ArrayDeque;

/**
 * Controlled Queue Delay algorithm to prevent queue overloading.
 *
 * <p>旨在：控制队列/buffer中的延迟.</p>
 * <p>队列满载视角：不是队列长度，而是队列中的数据包的驻留时间.</p>
 * <p>buffer bloat的根因是数据包在队列中的驻留时间过长，超过了有效的处理时间（SLA定义的时间或者重试时间），导致处理到的数据包都已经超时.</p>
 *
 * @see <a href="https://github.com/apache/hbase/blob/master/hbase-server/src/main/java/org/apache/hadoop/hbase/ipc/AdaptiveLifoCoDelCallQueue.java">HBase AdaptiveLifoCoDelCallQueue</a>
 * @see <a href="https://queue.acm.org/detail.cfm?id=2209336">CoDel Paper</a>
 */
class CoDelQueue {
    private ArrayDeque<Packet> queue = new ArrayDeque<>();

    @Heuristics
    private final long targetSojournTimeMs = 5;
    @Heuristics
    private final long intervalMs = 100;

    private long minDelayMs = Long.MIN_VALUE;
    private long lastUpdateTime = System.currentTimeMillis(); // 记录上次更新时间

    public void enqueue(Packet packet) {
        queue.add(packet);
    }

    public Packet dequeue() {
        if (queue.isEmpty()) {
            minDelayMs = Long.MAX_VALUE;
            return null;
        }

        long now = System.currentTimeMillis();
        long sojournTime = getCurrentSojournTime(now);
        if (sojournTime < targetSojournTimeMs) {
            // 当前队首数据包的逗留时间小于目标逗留时间，无需操作
            minDelayMs = Long.MAX_VALUE;
            return null;
        }

        // 如果当前逗留时间超过了目标值并且自上次更新以来的时间超过了间隔时间，可能需要丢弃
        if (now - lastUpdateTime > intervalMs) {
            lastUpdateTime = now;
            if (sojournTime < minDelayMs) {
                minDelayMs = sojournTime;
            } else {
                minDelayMs = Long.MAX_VALUE;
                return queue.poll(); // 超时，移除并返回队首数据包
            }
        }

        return null; // 在其他所有情况下，不操作队列并返回null
    }

    private long getCurrentSojournTime(long nowMs) {
        if (queue.isEmpty()) {
            return 0;
        }

        return queue.peek().sojournTime(nowMs);
    }

}
