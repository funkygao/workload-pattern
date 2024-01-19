package io.github.workload.integration.kafka;

import io.github.workload.annotations.WIP;
import org.apache.kafka.clients.producer.Partitioner;
import org.apache.kafka.common.Cluster;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

// Deficit Round Robin (DRR) 是一种排程算法，广泛用于网络调度，确保带宽分配的公平性和效率性
// 它能够处理不同服务级别的数据包，使得高优先级的数据包不会被低优先级的数据包淹没，同事确保低优先级的数据包依旧有机会得到处理
// DRR是对WRR(Weighted Round Robin)的改进，尤其适用于在各个队列中有不同大小数据包的情况；而WRR是各个队列事先固定权重
// DRR为每个队列分配一个Deficit Counter（透支计数器），每次轮到某个队列时，其Deficit Counter会增加一个固定的量（称为Quantum，即队列的权重）。如果Deficit Counter的值大于或等于队列前面包的大小，那么这个包就能够被发送，并且透支计数器的值减去该数据包的大小。如果包太大而不能被发送，那么它将等待下一个轮转。透支计数器不会在轮转中被清零，允许未发送的数据包在后续的轮转中得到发送的机会。
// https://github.com/funkygao/drr
@WIP
public class DRRBasedPartitioner implements Partitioner {
    // 为每个队列分配一个透支计数器
    private Map<Integer /* partition */, AtomicInteger> partitionDeficitCounters = new ConcurrentHashMap<>();

    @Override
    public int partition(String topic, Object key, byte[] keyBytes, Object value, byte[] valueBytes, Cluster cluster) {
        int priority = getPriorityFromKey(key);
        //int partitions = cluster.availablePartitionsForTopic(topic).size();
        int partitions = 8;

        int selectedPartition = 0;
        int maxDeficit = Integer.MIN_VALUE;

        // 寻找拥有最大信用额度的分区
        for (int partition = 0; partition < partitions; partition++) {
            // 初始化该分区的信用额度
            partitionDeficitCounters.putIfAbsent(partition, new AtomicInteger(0));

            // 每次轮到某个队列时，其Deficit Counter会增加一个固定的量（称为Quantum，即队列的权重）

            final int deficitWeight = getPriorityWeight(priority);
            final AtomicInteger partitionDeficit = partitionDeficitCounters.get(partition);
            // 高优先级的消息让分区的 `deficit` 更快的增长，从而有更高的机会被选中
            final int currentDeficit = partitionDeficit.addAndGet(deficitWeight);
            if (currentDeficit > maxDeficit) {
                selectedPartition = partition;
                maxDeficit = currentDeficit;
            }
        }

        // 从选择的分区的deficit中扣除一个量（可能是固定的或根据情况变动的）
        AtomicInteger selectedPartitionDeficit = partitionDeficitCounters.get(selectedPartition);
        final int deficitWeight = getPriorityWeight(priority);
        selectedPartitionDeficit.addAndGet(-deficitWeight);

        return selectedPartition;
    }

    @Override
    public void close() {

    }

    @Override
    public void configure(Map<String, ?> configs) {
    }

    private int getPriorityFromKey(Object key) {
        Integer B = (Integer) key;
        return B;
    }

    private int getPriorityWeight(int priority) {
        return priority;
    }

    @Test
    void demo() {
        final Logger log = LoggerFactory.getLogger("DRR");
        DRRBasedPartitioner partitioner = new DRRBasedPartitioner();
        Map<Integer, AtomicInteger> partitionHistogram = new TreeMap<>();
        Map<Integer, List<Integer>> priorityHistogram = new TreeMap<>();
        for (int i = 0; i < 1000; i++) {
            int priority = ThreadLocalRandom.current().nextInt(10);
            int partition = partitioner.partition("topic", priority, null, null, null, null);
            priorityHistogram.computeIfAbsent(priority, key -> new LinkedList<>()).add(partition);
            partitionHistogram.computeIfAbsent(partition, key -> new AtomicInteger()).incrementAndGet();
        }

        for (Integer partition : partitionHistogram.keySet()) {
            log.info("{} {}", partition, partitionHistogram.get(partition).get());
        }

        for (Integer priority : priorityHistogram.keySet()) {
            List<Integer> partitions = priorityHistogram.get(priority);
            Collections.sort(partitions);
            log.info("P:{} {}", priority, partitions);
        }
    }
}
