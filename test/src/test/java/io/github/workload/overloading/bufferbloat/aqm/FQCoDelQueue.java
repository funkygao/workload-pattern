package io.github.workload.overloading.bufferbloat.aqm;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fair queueing CoDel.
 */
class FQCoDelQueue implements QueueDiscipline {
    private final Map<Integer /* quadruples */, CoDelQueue> flowQueues;
    // 用于确定一个流在一个时间间隔内可以发送多少数据
    private final long quantum;

    FQCoDelQueue(long quantum) {
        this.quantum = quantum;
        flowQueues = new LinkedHashMap<>();
    }

    @Override
    public void enqueue(Packet packet) {
        CoDelQueue queue = flowQueues.computeIfAbsent(packet.quadruples(), k -> new CoDelQueue());
        queue.enqueue(packet);
    }

    @Override
    public Packet dequeue() {
        // 实现FQ的排队策略。我们可以使用一个简单的轮询机制，也可以使用更复杂的调度算法
        for (CoDelQueue queue : flowQueues.values()) {
            Packet packet = queue.dequeue();
            if (packet != null) {
                return packet;
            }
        }

        return null;
    }

}
