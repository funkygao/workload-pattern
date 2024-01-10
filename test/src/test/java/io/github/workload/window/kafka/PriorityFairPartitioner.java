package io.github.workload.window.kafka;

import io.github.workload.WorkloadPriority;
import io.github.workload.window.CountRolloverStrategy;
import io.github.workload.window.CountWindowState;
import io.github.workload.window.TumblingWindow;
import io.github.workload.window.WindowConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.Partitioner;
import org.apache.kafka.common.Cluster;
import org.apache.kafka.common.PartitionInfo;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
class PriorityFairPartitioner implements Partitioner {
    private TumblingWindow<CountWindowState> window;

    /**
     * {@inheritDoc}
     */
    @Override
    public int partition(String topic, Object key, byte[] keyBytes, Object value, byte[] valueBytes, Cluster cluster) {
        WorkloadPriority priority = WorkloadPriority.fromP(5);
        window.advance(priority);

        List<PartitionInfo> partitions = cluster.partitionsForTopic(topic);
        int numPartitions = partitions.size();
        List<PartitionInfo> availablePartitions = cluster.availablePartitionsForTopic(topic);
        int numAvailablePartitions = availablePartitions.size();
        if (numAvailablePartitions == 0) {
            return (ThreadLocalRandom.current().nextInt() & Integer.MAX_VALUE) % numPartitions;
        } else if (numAvailablePartitions == 1) {
            return availablePartitions.get(0).partition();
        }

        return availablePartitions.get(4).partition();
    }

    @Override
    public void configure(Map<String, ?> configs) {
        WindowConfig<CountWindowState> config = WindowConfig.create(0, 2000,
                new CountRolloverStrategy() {
                    @Override
                    public void onRollover(long nowNs, CountWindowState state, TumblingWindow<CountWindowState> window) {
                        // 调整不同优先级消息的partition分配策略
                    }
                });
        window = new TumblingWindow(config, "MQ", 0);
    }

    @Override
    public void close() {
    }
}
