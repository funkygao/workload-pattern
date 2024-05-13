package io.github.workload.greedy;

import io.github.workload.CostAware;
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

    /**
     * 处理大数据集分批任务的方法，无返回值场景.
     *
     * @param items             要处理的数据集
     * @param config            大报文保护的配置
     * @param partitionConsumer 处理每一个({@link Partition})数据处理者
     * @param <IN>              输入数据类型
     * @param <E>               异常类型
     * @throws E 如果在处理数据时发生异常
     */
    public <IN, E extends Throwable> void invoke(List<IN> items, GreedyConfig config, @NonNull ThrowingConsumer<Partition<IN>, E> partitionConsumer) throws E {
        processItems(items, config.getPartitionSize(), partition -> {
            partitionConsumer.accept(partition);
            return null; // 用于void方法
        }, config);
    }

    /**
     * 处理大数据集分批任务的方法，有返回值场景.
     *
     * @param items             要处理的数据集
     * @param config            大报文保护的配置
     * @param partitionFunction 处理每一个({@link Partition})数据处理者
     * @param <OUT>             结果数据类型
     * @param <IN>              输入数据类型
     * @param <E>               异常类型
     * @return 结果集
     * @throws E 如果在处理数据时发生异常
     */
    public <OUT, IN, E extends Throwable> List<OUT> invoke(List<IN> items, GreedyConfig config, @NonNull ThrowingFunction<Partition<IN>, List<OUT>, E> partitionFunction) throws E {
        return processItems(items, config.getPartitionSize(), partitionFunction, config);
    }

    private <OUT, IN, E extends Throwable> List<OUT> processItems(List<IN> items, int partitionSize, ThrowingFunction<Partition<IN>, List<OUT>, E> processor, GreedyConfig config) throws E {
        if (items == null || items.isEmpty()) {
            return new ArrayList<>();
        }

        List<OUT> results = new ArrayList<>();
        int itemsProcessed = 0;
        int costs = 0;
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
                // fail fast to avoid escaping to production environment
                throw new IllegalStateException("BUG! Partition not accessed, accessing the whole dataset?");
            }

            if (config.getGreedyLimiter() != null) {
                final String key = config.getLimiterKey();
                if (key == null || key.trim().isEmpty()) {
                    log.error("BUG! greedyLimiter not null while limiterKey empty!");
                } else {
                    costs += partition.costs();
                    if (costs > config.getCostsThreshold()) {
                        if (!config.getGreedyLimiter().canAcquire(key, 1)) {
                            throw new GreedyException();
                        }
                    }
                }
            }
        }

        if (itemsProcessed > config.getGreedyThreshold()) {
            config.getThresholdExceededAction().accept(itemsProcessed);
        }

        return results;
    }

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

        public int costs() {
            int costs = 0;
            if (items != null && !items.isEmpty() && items.get(0) instanceof CostAware) {
                for (T item : items) {
                    CostAware costAware = (CostAware) item;
                    costs += costAware.cost();
                }
            }
            return costs;
        }
    }

}
