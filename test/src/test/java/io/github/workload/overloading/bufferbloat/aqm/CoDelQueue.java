package io.github.workload.overloading.bufferbloat.aqm;

import io.github.workload.annotations.Heuristics;
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
    private static final Logger log = LoggerFactory.getLogger(CoDelQueue.class.getSimpleName());

    @Heuristics
    private static final long INTERVAL = 100; // congested window size in ms
    @Heuristics
    private static final long TARGET = 5; // 目标延迟时间（单位：毫秒）

    private Queue<Packet> queue;

    // 连续拥塞窗口期的起始时间
    private long firstAboveTime;

    // time to drop next packet, 下一次丢包应当发生的时间点，每次丢包时重新计算
    private long dropNext;

    // 窗口期内总计丢弃多少数据包
    private int droppedCount; // 在当前dropNext周期内丢包的总计数

    public CoDelQueue() {
        queue = new LinkedList<>();
        dropNext = 0;
        droppedCount = 0;
        firstAboveTime = 0;
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
            log.debug("queue is empty");
            stopCongestedWindow();
            return null;
        }

        Packet packet = queue.peek(); // 检查队列前端的包
        long sojournTime = packet.sojournTime(now);
        if (sojournTime < TARGET) {
            // congestion window要求窗口内每一个包排队时间超限，只要有一个不超限就关闭窗口
            log.debug("{} sojournTime:{}, not delayed", packet, sojournTime);
            stopCongestedWindow();
            return queue.poll();
        }

        // `firstAboveTime` 是用于触发 CoDel 算法第一次进入丢包状态的机制
        // `dropNext` 是在已经确定存在持续拥塞之后用于控制后续丢包行动的时间调度

        //  sojournTime > target         now          dropNext
        //             |                  |              |
        //             |<- interval ->|   V<- interval ->|
        //-------------+--------------+---+--------------+-------------------> ∞
        //                            |                  |<- drop window  ->|
        //                      firstAboveTime
        //                            |<-  congestion window              ->|

        log.debug("{} sojournTime:{}, delayed", packet, sojournTime);
        if (firstAboveTime == 0) {
            // 首次检测到拥塞，开启新拥塞窗口，但不丢弃包
            startCongestedWindow(now);
            log.debug("start new window: {}", firstAboveTime);
            return queue.poll();
        }

        if (!inCongestedWindow(now)) {
            // 拥塞窗口还没有攒够，不丢包
            log.debug("now:{} not within window:{}", now, firstAboveTime);
            return queue.poll();
        }

        // 仍在持续拥塞窗口期内
        if (now > dropNext) {
            // This drop indicates we're in a congestion period, so drop the current packet
            log.debug("now:{} > dropNext:{}, will drop head packet", now, dropNext);

            Packet droppedPacket = queue.poll();
            log.debug("dropped:{}, will dequeue next candidate", droppedPacket);

            scheduleDropNext(now);
            return droppedPacket.drop();
        }

        // The current time is inside the current congestion window, but before dropNext.
        // dropNext 时间点尚未到来，继续观察，直到到达 `dropNext` 时间点再进行丢包决策
        log.debug("dropNext:{}, wait for dropNext", dropNext);
        return queue.poll();
    }

    @Override
    public Packet dequeue() {
        return dequeue(System.currentTimeMillis());
    }

    private void stopCongestedWindow() {
        if (firstAboveTime == 0) {
            return;
        }

        log.debug("stop window");
        this.firstAboveTime = 0;
        this.droppedCount = 0;
    }

    private void startCongestedWindow(long now) {
        this.firstAboveTime = now + INTERVAL;
    }

    private boolean inCongestedWindow(long now) {
        return firstAboveTime != 0 && now > firstAboveTime;
    }

    // CoDel算法中丢包的逻辑：根据count计算dropNext
    private void scheduleDropNext(long now) {
        droppedCount++;

        if (droppedCount == 1) {
            // 若是第一次丢包，则设置下一次丢包时间
            dropNext = now + INTERVAL;
        } else {
            // We have dropped a packet before, 以指数退避计算下次丢包时间
            dropNext = controlLaw(dropNext);
        }
        log.debug("droppedCount:{}, dropNext:{}, window:{}", droppedCount, dropNext, firstAboveTime);
    }

    // 控制律用于动态计算下一次丢包的时间
    // 指数退避的方式逐渐增加间隔，避免过度拥塞
    // 基于这样一个假设：
    // 如果网络队列中的延迟是由暂时的拥塞引起的，那么丢弃几个数据包后，队列可以恢复正常
    // 而如果拥塞是持续存在的，那么算法会以指数退避的方式逐步增加丢包的间隔，减少丢包可能导致的干扰和不必要的吞吐量损失
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
        long[] times = new long[]{2, 5, 8, 30, 109, 121, 231, 285, 287, 301, 323, 500};
        List<Integer> egress = new LinkedList<>();
        for (int i = 0; i < N; i++) {
            long now = times[i];
            Packet packet = queue.dequeue(now);
            if (packet == null) {
                log.info("isEmpty:{}", queue.isEmpty());
            }
            if (!packet.shouldDrop()) {
                egress.add(packet.id());
            } else {
                log.warn("{} dropped", packet);
            }

        }
        log.info("{}, {}", egress.size(), egress);
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
