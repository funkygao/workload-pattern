package io.github.workload.overloading.bufferbloat.aqm;

import io.github.workload.annotations.WIP;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fair queueing/FlowQueue CoDel.
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/draft-ietf-aqm-fq-codel">FQ-CoDel RFC</a>
 * @see <a href="https://man7.org/linux/man-pages/man8/tc-fq_codel.8.html">tc-fq_codel man page</a>
 * @see <a href="https://github.com/torvalds/linux/blob/master/net/sched/sch_fq_codel.c">Linux Implementation</a>
 */
@WIP
class FQCoDelQueue implements QueueDiscipline {
    // with only one queue, FQ-CoDel behaves essentially the same as CoDel
    private static final int FLOWS = 1024;

    // DRR为每个队列分配一个透支计数器
    private final Map<Integer /* quadruples */, Flow> flowQueues;

    // The maximum amount of bytes to be dequeued from a queue at once
    // 通常为MTU
    private final long quantum;

    FQCoDelQueue(long quantum) {
        this.quantum = quantum;
        flowQueues = new LinkedHashMap<>();
    }

    @Override
    public void enqueue(Packet packet) {
        Flow flow = classify(packet);
        flow.enqueue(packet);
    }

    private Flow classify(Packet packet) {
        final int key = packet.quadruples() % FLOWS;
        return flowQueues.computeIfAbsent(key, k -> new Flow());
    }

    @Override
    public Packet dequeue() {
        // the scheduler which selects which queue to dequeue a packet from

        // dequeue by a two-tier round-robin scheme, in which each queue is allowed to dequeue up
        //  to a configurable quantum of bytes for each iteration(Deficit Round Robin)
        Iterator<Map.Entry<Integer, Flow>> iterator = flowQueues.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, Flow> entry = iterator.next();
            Flow flow = entry.getValue();

            if (flow.deficit <= 0) {
                flow.deficit += quantum;
                continue;
            }

            Packet packet;
            while ((packet = flow.dequeue()) != null) {
                long packetSize = packet.size();
                if (flow.deficit >= packetSize) {
                    // The packet can be sent, subtract its size from the deficit counter
                    flow.deficit -= packetSize;
                    return packet;
                } else {
                    // Not enough deficit, put the packet back and break to check next queue
                    // 包太大而不能被发送，那么它将等待下一个轮转
                    flow.enqueueFront(packet);
                    break;
                }
            }

            // Remove empty queues to prevent memory leak
            if (flow.isEmpty()) {
                iterator.remove();
            }
        }

        return null;
    }

    private static class Flow {
        private CoDelQueue queue;
        private long deficit; // 信用额度/透支计数器

        Flow() {
            queue = new CoDelQueue();
            deficit = 0;
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
