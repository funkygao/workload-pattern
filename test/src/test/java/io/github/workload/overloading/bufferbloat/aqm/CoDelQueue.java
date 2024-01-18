package io.github.workload.overloading.bufferbloat.aqm;

import io.github.workload.annotations.Heuristics;
import io.github.workload.annotations.Immutable;
import io.github.workload.annotations.WIP;
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
 * <p>CoDel算法解决了buffer bloat问题，但没有解决多链接处理的公平性问题，各种链接占用同一个队列，那么数据量大的的连接势必数据包就更多，它挤占队列的能力就更强.</p>
 * <p>因此，产生了fq-codel，fair/flow queue codel.</p>
 *
 * @see <a href="https://github.com/apache/hbase/blob/master/hbase-server/src/main/java/org/apache/hadoop/hbase/ipc/AdaptiveLifoCoDelCallQueue.java">HBase AdaptiveLifoCoDelCallQueue</a>
 * @see <a href="https://queue.acm.org/detail.cfm?id=2209336">CoDel Paper</a>
 */
@WIP
class CoDelQueue implements QueueDiscipline {
    private static final Logger log = LoggerFactory.getLogger(CoDelQueue.class.getSimpleName());

    private static final DequeueResult QUEUE_WAS_EMPTY = new DequeueResult(null, false);

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

    @Override
    public void enqueue(Packet packet) {
        enqueue(packet, System.nanoTime());
    }

    @Override
    public Packet dequeue() {
        return dequeue(System.nanoTime());
    }

    boolean isEmpty() {
        return queue.isEmpty();
    }

    void enqueue(Packet packet, long now) {
        packet.enqueue(now);
        queue.offer(packet);
    }

    Packet dequeue(long now) {
        DequeueResult res = doDequeue(now);
        if (res == QUEUE_WAS_EMPTY) {
            dropping = false;
            return null;
        }

        if (dropping) {
            // 根据`dropNext`时间和`droppedCount`判断是否继续丢包
            if (!res.okToDrop) {
                // End dropping, as no need to drop
                dropping = false;
            } else {
                // It's time to drop, enqueue time of next drop packet is already set
                while (now >= dropNext && !queue.isEmpty()) {
                    Packet droppedPacket = queue.poll();
                    log.info("dropped packet: {}", droppedPacket);
                    droppedCount++;
                    if (droppedCount > 1 && now - dropNext < INTERVAL) {
                        // Adjust the dropping count only if it's not the first packet and the time didn't pass the full INTERVAL yet
                        droppedCount = Math.max(droppedCount - 2, 1);
                    }
                    // If more packets need to be dropped, reschedule the next drop using control law
                    dropNext = controlLaw(dropNext, droppedCount);
                }
            }
        } else {
            if (res.okToDrop) {
                // It's not currently dropping, but it's ok to start
                dropping = true;
                droppedCount = 1;
                queue.poll(); // Actually remove the packet
                dropNext = controlLaw(now, droppedCount);
            }
        }

        return res.packet;
    }

    private DequeueResult doDequeue(long now) {
        //      firstAboveTime
        //             |    interval     |
        // ----------------------------------------> time
        //             XX  X XXX     X X X
        //              |
        //            packet
        Packet packet = queue.peek(); // Peek the head but don't remove yet
        if (packet == null) {
            firstAboveTime = 0;
            return QUEUE_WAS_EMPTY;
        }

        long sojournTime = packet.sojournTime(now);
        if (sojournTime < TARGET) {
            firstAboveTime = 0;
            return new DequeueResult(packet, false);
        } else {
            // this packet stays longer than target
            if (firstAboveTime == 0) {
                firstAboveTime = now + INTERVAL;
            } else if (now >= firstAboveTime) {
                return new DequeueResult(packet, true);
            }
        }

        return new DequeueResult(packet, false);
    }

    private long controlLaw(long whence, int droppedCount) {
        // 丢包越多，那么下一次丢包时间越近
        return whence + (INTERVAL / (long) Math.sqrt(droppedCount));
    }

    @Immutable
    private static class DequeueResult {
        final Packet packet;
        final boolean okToDrop;

        DequeueResult(Packet packet, boolean okToDrop) {
            this.packet = packet;
            this.okToDrop = okToDrop;
        }
    }

    @Test
    void nextDrop() {
        CoDelQueue codelQueue = new CoDelQueue();
        log.info("{}", CoDelQueue.INTERVAL);
        for (int droppedCount = 1; droppedCount < 10; droppedCount++) {
            log.info("{}", codelQueue.controlLaw(0, droppedCount));
        }
        log.info("{}", codelQueue.controlLaw(0, 1000));
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
        for (int i = 0; i < N; i++) {
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
