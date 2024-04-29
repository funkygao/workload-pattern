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

    public <IN, E extends Throwable> void invoke(List<IN> items, int partitionSize, @NonNull ThrowingConsumer<Partition<IN>, E> partitionConsumer, GreedyConfig config) throws E {
        processItems(items, partitionSize, partition -> {
            partitionConsumer.accept(partition);
            return null; // 用于void方法
        }, config);
    }

    public <OUT, IN, E extends Throwable> List<OUT> invoke(List<IN> items, int partitionSize, @NonNull ThrowingFunction<Partition<IN>, List<OUT>, E> partitionFunction, GreedyConfig config) throws E {
        return processItems(items, partitionSize, partitionFunction, config);
    }

    private <OUT, IN, E extends Throwable> List<OUT> processItems(List<IN> items, int partitionSize, ThrowingFunction<Partition<IN>, List<OUT>, E> processor, GreedyConfig config) throws E {
        validateConfig(partitionSize, config);
        if (items == null || items.isEmpty()) {
            return new ArrayList<>();
        }

        List<OUT> results = new ArrayList<>();
        int itemsProcessed = 0;
        for (int start = 0, partitionId = 0; start < items.size(); start += partitionSize, partitionId++) {
            final int end = Math.min(items.size(), start + partitionSize);
            List<IN> partitionData = items.subList(start, end);
            Partition<IN> partition = new Partition<>(partitionData, partitionId);
            List<OUT> partitionResult = processor.apply(partition);
            if (partitionResult != null) {
                results.addAll(partitionResult);
            }
            itemsProcessed += partitionData.size();
            if (!partition.accessed) {
                throw new IllegalStateException("BUG! Partition not accessed, accessing the whole dataset?");
            }
        }

        if (itemsProcessed > config.greedyThreshold) {
            if (config.thresholdExceededAction != null) {
                config.thresholdExceededAction.execute(itemsProcessed);
            } else {
                log.warn("items processed:{} > {}", itemsProcessed, config.greedyThreshold);
            }

        }

        return results;
    }

    private void validateConfig(int partitionSize, GreedyConfig config) {
        if (partitionSize <= 0) {
            throw new IllegalArgumentException("partitionSize must be greater than 0");
        }
        if (config.greedyThreshold <= partitionSize) {
            throw new IllegalArgumentException("greedyThreshold must be greater than partitionSize");
        }
    }

    @RequiredArgsConstructor
    public class Partition<T> {
        private final List<T> items;
        @Getter
        private final int id;
        private boolean accessed = false;

        public List<T> getItems() {
            accessed = true;
            return items;
        }
    }

    public class GreedyConfig {
        private final int greedyThreshold;
        private final ThresholdExceededAction thresholdExceededAction;

        public GreedyConfig(int greedyThreshold, ThresholdExceededAction thresholdExceededAction) {
            this.greedyThreshold = greedyThreshold;
            this.thresholdExceededAction = thresholdExceededAction;
        }

        public static GreedyConfig of(int greedyThreshold, ThresholdExceededAction action) {
            return new GreedyConfig(greedyThreshold, action);
        }
    }

    public interface ThresholdExceededAction {
        void execute(int itemsProcessed);
    }
}
