package io.github.workload.overloading.bufferbloat.aqm;

import io.github.workload.annotations.VisibleForTesting;
import io.github.workload.annotations.WIP;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
 * @see <a href="https://blog.rabbitmq.com/posts/2012/05/some-queuing-theory-throughput-latency-and-bandwidth/">RabbitMQ的QoS应用</a>
 * @see <a href="https://queue.acm.org/detail.cfm?id=2209336">CoDel Paper</a>
 * @see <a href="https://github.com/facebook/folly/blob/bd600cd4e88f664f285489c76b6ad835d8367cd2/folly/executors/Codel.cpp">Facebook RPC CoDel</a>
 */
@WIP
class CoDelQueue implements QueueDiscipline {
    // CoDel算法的一些参数
    private static final long INTERVAL = 100; // CoDel算法定时周期的时间长度（单位：毫秒）
    private static final long TARGET = 5; // 目标延迟时间（单位：毫秒）

    private Queue<Packet> queue; // 用于存储网络数据包的队列
    private long firstAboveTime; // 第一个数据包延迟超过目标值的时间
    private long dropNext; // 下一次丢包应当发生的时间
    private int droppedCount; // 在当前dropNext周期内丢包的总计数

    public CoDelQueue() {
        queue = new LinkedList<>();
        firstAboveTime = 0;
        dropNext = 0;
        droppedCount = 0;
    }

    @VisibleForTesting
    void enqueue(Packet packet, long now) {
        packet.enqueue(now);
        queue.add(packet);
    }

    @Override
    public void enqueue(Packet packet) {
        enqueue(packet, System.currentTimeMillis());
    }

    @VisibleForTesting
    Packet dequeue(long now) {
        if (queue.isEmpty()) {
            // 队列空时重置状态变量
            firstAboveTime = 0;
            return null;
        }

        Packet packet = queue.peek(); // 检查队列前端的包
        long sojournTime = packet.sojournTime(now);
        if (sojournTime < TARGET) {
            // 延迟没有超过目标值，重置状态
            firstAboveTime = 0;
            return queue.poll();
        }

        // 延迟超过目标值
        if (firstAboveTime == 0) {
            // 记录第一次超过延迟目标值的时间
            firstAboveTime = now + INTERVAL;
        } else if (now >= firstAboveTime) {
            // 延迟持续时间超过了阈值

            // 检查是否应当丢包
            if (now >= dropNext) {
                doDrop(now);

                dequeue(); // 递归，取下一个不drop的包
            }
        }
        return queue.poll();
    }

    private long getSloughTimeout() {
        return 2 * TARGET;
    }

    @Override
    public Packet dequeue() {
        return dequeue(System.currentTimeMillis());
    }

    // CoDel算法中丢包的逻辑：根据count计算dropNext
    private void doDrop(long now) {
        queue.poll();

        // 若是第一次丢包，则设置下一次丢包时间
        if (droppedCount == 0) {
            droppedCount = 1;
            dropNext = now + INTERVAL;
        } else {
            // 以指数退避计算下次丢包时间
            if (now > dropNext + INTERVAL) {
                droppedCount = 1;
            } else {
                droppedCount *= 2;
            }
            dropNext = controlLaw(dropNext);
        }
        firstAboveTime = 0;
    }

    // 控制律用于动态计算下一次丢包的时间
    private long controlLaw(long dropNext) {
        return (long) (dropNext + INTERVAL / Math.sqrt(droppedCount));
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }

    @Test
    @Disabled
    void demo() throws InterruptedException {
        final Logger log = LoggerFactory.getLogger("CoDelQueue");

        CoDelQueue queue = new CoDelQueue();
        final int N = 10;

        // Simulate packet arrivals and processing
        for (int i = 0; i < N; i++) {
            Packet packet = new Packet(i); // Create a new packet
            queue.enqueue(packet, 1); // Enqueue packet
        }

        // deque
        long[] times = new long[]{2, 5, 8, 30, 35, 39, 40, 41, 46, 66, 89, 100};
        List<Packet> egress = new LinkedList<>();
        for (int i = 0; i < N; i++) {
            long now = times[i];
            Packet packet = queue.dequeue(now);
            if (packet == null) {
                log.info("isEmpty:{}", queue.isEmpty());
            }
            egress.add(packet);
        }
        log.info("{}", egress);
    }

    @Test
    @Disabled
    void controlLaw() {
        CoDelQueue queue = new CoDelQueue();
        List<Long> dropNextList = new LinkedList<>();
        for (int droppedCount = 1; droppedCount < 500; droppedCount += 20) {
            queue.droppedCount = droppedCount;
            dropNextList.add(queue.controlLaw(0));
        }
        // setting drops packets at increasingly shorter intervals to achieve a linear change in throughput
        String expected = "[100, 21, 15, 12, 11, 9, 9, 8, 7, 7, 7, 6, 6, 6, 5, 5, 5, 5, 5, 5, 4, 4, 4, 4, 4]";
        assertEquals(expected, dropNextList.toString());
    }

}
