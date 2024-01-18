package io.github.workload.overloading.bufferbloat.aqm;

import io.github.workload.annotations.WIP;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fair queueing CoDel.
 */
@WIP
class FQCoDelQueue implements QueueDiscipline {
    // DRR为每个队列分配一个透支计数器
    private final Map<Integer /* quadruples */, QueueState> flowQueues;

    // 用于确定一个流在一个时间间隔内可以发送多少数据，通常为MTU
    private final long quantum;

    FQCoDelQueue(long quantum) {
        this.quantum = quantum;
        flowQueues = new LinkedHashMap<>();
    }

    @Override
    public void enqueue(Packet packet) {
        QueueState state = flowQueues.computeIfAbsent(packet.quadruples(), k -> new QueueState());
        state.enqueue(packet);
    }

    @Override
    public Packet dequeue() {
        // 队列之间使用针对包大小的DRR(Deficit Round Robin)调度算法进行调度
        Iterator<Map.Entry<Integer, QueueState>> iterator = flowQueues.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, QueueState> entry = iterator.next();
            QueueState queue = entry.getValue();

            // 每次轮到某个队列时，其Deficit Counter会增加一个固定的量（称为Quantum，即队列的权重）
            queue.deficitCounter += quantum;

            Packet packet;
            while ((packet = queue.dequeue()) != null) {
                long packetSize = packet.size();
                if (queue.deficitCounter >= packetSize) {
                    // The packet can be sent, subtract its size from the deficit counter
                    queue.deficitCounter -= packetSize;
                    return packet;
                } else {
                    // Not enough deficit, put the packet back and break to check next queue
                    // 包太大而不能被发送，那么它将等待下一个轮转
                    queue.enqueueFront(packet);
                    break;
                }
            }

            // Remove empty queues to prevent memory leak
            if (queue.isEmpty()) {
                iterator.remove();
            }
        }

        return null;
    }

    private static class QueueState {
        private CoDelQueue queue;
        private long deficitCounter; // 信用额度/透支计数器

        QueueState() {
            queue = new CoDelQueue();
            deficitCounter = 0;
        }

        void enqueue(Packet packet) {
            queue.enqueue(packet);
        }

        boolean isEmpty() {
            return queue.isEmpty();
        }

        Packet dequeue() {
            return queue.dequeue();
        }

        void enqueueFront(Packet packet) {

        }
    }

}
