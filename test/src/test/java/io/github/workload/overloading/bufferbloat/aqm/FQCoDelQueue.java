package io.github.workload.overloading.bufferbloat.aqm;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fair queueing CoDel.
 */
class FQCoDelQueue implements QueueDiscipline {
    private final Map<Integer /* quadruples */, QueueState> flowQueues;
    // 用于确定一个流在一个时间间隔内可以发送多少数据
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
            QueueState state = entry.getValue();
            // 某个队列每当获取出队机会时，重新设定其赤字值
            state.deficitCounter += quantum;

            Packet packet;
            while ((packet = state.dequeue()) != null) {
                long packetSize = packet.size();
                if (state.deficitCounter >= packetSize) {
                    // The packet can be sent, subtract its size from the deficit counter
                    state.deficitCounter -= packetSize;
                    return packet;
                } else {
                    // Not enough deficit, put the packet back and break to check next queue
                    state.enqueueFront(packet);
                    break;
                }
            }

            // Remove empty queues to prevent memory leak
            if (state.isEmpty()) {
                iterator.remove();
            }
        }

        return null;
    }

    private static class QueueState {
        private CoDelQueue queue;
        private long deficitCounter;

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
