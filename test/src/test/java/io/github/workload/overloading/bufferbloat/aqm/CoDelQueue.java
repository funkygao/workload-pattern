package io.github.workload.overloading.bufferbloat.aqm;

import io.github.workload.annotations.Heuristics;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

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
    @Heuristics
    private static final long TARGET = TimeUnit.MILLISECONDS.toNanos(5);
    @Heuristics
    private static final long INTERVAL = TimeUnit.MILLISECONDS.toNanos(100);

    private Queue<Packet> queue;
    private long firstAboveTime;
    private long dropNext; // Time to drop next packet
    private boolean dropping;
    private int droppedCount; // Packets dropped since going into drop state

    CoDelQueue() {
        this.queue = new LinkedList<>();
        this.firstAboveTime = 0;
        this.dropNext = 0;
        this.droppedCount = 0;
        this.dropping = false;
    }

    public void enqueue(Packet packet, long now) {
        packet.enqueue(now);
        queue.offer(packet);
    }

    public Packet dequeue(long now) {
        DequeueResult res = doDequeue(now);
        if (res.packet == null) {
            dropping = false;
            return null;
        }

        if (dropping) {
            if (!res.okToDrop) {
                dropping = false;
            } else if (now >= dropNext) {
                do {
                    queue.poll(); // Actually remove the packet
                    droppedCount++;
                    res = doDequeue(now);
                    if (!res.okToDrop) {
                        dropping = false;
                    } else {
                        dropNext = controlLaw(dropNext);
                    }
                } while (now >= dropNext && dropping);
            }
        } else if (res.okToDrop && ((now - dropNext < INTERVAL) || (now - firstAboveTime >= INTERVAL))) {
            queue.poll(); // Actually remove the packet
            dropping = true;
            droppedCount = now - dropNext < INTERVAL ? Math.max(droppedCount - 2, 1) : 1;
            dropNext = controlLaw(now);
        }

        return res.packet;
    }

    private DequeueResult doDequeue(long now) {
        Packet packet = queue.peek(); // Peek the head but don't remove yet
        if (packet == null) {
            firstAboveTime = 0;
            return new DequeueResult(null, false);
        } else {
            long sojournTime = packet.sojournTime(now);
            if (sojournTime < TARGET) {
                firstAboveTime = 0;
                return new DequeueResult(packet, false);
            } else {
                if (firstAboveTime == 0) {
                    firstAboveTime = now + INTERVAL;
                } else if (now >= firstAboveTime) {
                    return new DequeueResult(packet, true);
                }
            }
        }

        return new DequeueResult(packet, false);
    }

    // gradually increases the frequency of dropping until the queue is controlled(sojourn time goes below target)
    private long controlLaw(long t) {
        return t + (INTERVAL / (long) Math.sqrt(droppedCount));
    }

    private static class DequeueResult {
        Packet packet;
        boolean okToDrop;

        DequeueResult(Packet packet, boolean okToDrop) {
            this.packet = packet;
            this.okToDrop = okToDrop;
        }
    }

    @Test
    @Disabled
    void demo() throws InterruptedException {
        final Logger log = LoggerFactory.getLogger("CoDelQueue");

        CoDelQueue codelQueue = new CoDelQueue();
        final int N = 100;

        // Simulate packet arrivals and processing
        for (int i = 0; i < N; i++) {
            Packet packet = new Packet(i); // Create a new packet
            long now = System.nanoTime();
            codelQueue.enqueue(packet, now); // Enqueue packet
            Thread.sleep(5); // Simulate processing delay
        }

        // Dequeue packets
        for (int i = 0; i < N * 2; i++) {
            long now = System.nanoTime();
            Packet dequeuedPacket = codelQueue.dequeue(now);
            if (dequeuedPacket == null) {
                log.info("{}, got null", i);
            } else {
                log.info("{} Packet:{} sojourn time:{}", i, dequeuedPacket, dequeuedPacket.sojournTime(now));
            }

            Thread.sleep(5 + ThreadLocalRandom.current().nextInt(10)); // Simulate processing delay
        }
    }

}
