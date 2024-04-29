package io.github.workload.greedy;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * 大报文(贪婪工作负荷)安全的分批处理大数据集的工具类.
 */
@UtilityClass
@Slf4j
public class GreedySafeInvoker {

    public <IN, E extends Throwable> void invoke(List<IN> items, int partitionSize, @NonNull ThrowingConsumer<Partition<IN>, E> partitionConsumer, int greedyThreshold) throws E {
        if (partitionSize <= 0) {
            throw new IllegalArgumentException("partitionSize must be greater than 0");
        }
        if (greedyThreshold <= partitionSize) {
            throw new IllegalArgumentException("greedyThreshold must be greater than partitionSize");
        }
        if (items == null || items.isEmpty()) {
            return;
        }

        int partitionId = 0;
        int itemsProcessed = 0;
        for (int start = 0; start < items.size(); start += partitionSize) {
            final int end = Math.min(items.size(), start + partitionSize);
            // items.subList不会创建原始列表的物理副本，它只是创建了一个视图，不增加GC压力
            List<IN> partitionData = items.subList(start, end);
            Partition<IN> partition = new Partition<>(partitionData, partitionId);
            partitionConsumer.accept(partition);
            if (!partition.accessed) {
                throw new IllegalStateException("BUG! Partition<" + items.get(0).getClass().getSimpleName() + "> not accessed, accessing the whole dataset?");
            }

            itemsProcessed += partitionData.size();
            partitionId++;
        }

        if (itemsProcessed > greedyThreshold) {
            log.warn("items processed:{} > {}", itemsProcessed, greedyThreshold);
        }
    }

    public <OUT, IN, E extends Throwable> List<OUT> invoke(List<IN> items, int partitionSize, @NonNull ThrowingFunction<Partition<IN>, List<OUT>, E> partitionFunction, int greedyThreshold) throws E {
        if (partitionSize <= 0) {
            throw new IllegalArgumentException("partitionSize must be greater than 0");
        }
        if (greedyThreshold <= partitionSize) {
            throw new IllegalArgumentException("greedyThreshold must be greater than partitionSize");
        }
        if (items == null || items.isEmpty()) {
            return new ArrayList<>();
        }

        List<OUT> results = new ArrayList<>(items.size());
        int partitionId = 0;
        int itemsProcessed = 0;
        for (int start = 0; start < items.size(); start += partitionSize) {
            final int end = Math.min(items.size(), start + partitionSize);
            // items.subList不会创建原始列表的物理副本，它只是创建了一个视图，不增加GC压力
            List<IN> partitionData = items.subList(start, end);
            Partition<IN> partition = new Partition<>(partitionData, partitionId);
            List<OUT> partitionResult = partitionFunction.accept(partition);
            if (!partition.accessed) {
                throw new IllegalStateException("BUG! Partition<" + items.get(0).getClass().getSimpleName() + "> not accessed, accessing the whole dataset?");
            }

            results.addAll(partitionResult);

            itemsProcessed += partitionData.size();
            partitionId++;
        }

        if (itemsProcessed > greedyThreshold) {
            log.warn("items processed:{} > {}", itemsProcessed, greedyThreshold);
        }

        return results;
    }

    // 避免partition处理器误用问题：本该范围子集却访问了全集
    @RequiredArgsConstructor
    public static class Partition<T> {
        private final List<T> items;
        @Getter
        private final int id;
        private boolean accessed = false;

        public List<T> getItems() {
            accessed = true;
            return items;
        }
    }
}
