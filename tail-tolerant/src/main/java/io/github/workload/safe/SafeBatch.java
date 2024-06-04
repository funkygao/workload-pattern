package io.github.workload.safe;

import com.google.common.collect.Lists;
import io.github.workload.CostAware;
import io.github.workload.greedy.GreedyConfig;
import io.github.workload.greedy.GreedyException;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * 大报文安全的分批处理大数据集，抑制方法内(无法使用AOP)的大报文风险.
 *
 * <p>NOTE：使用者要确保原始数据不会被意外修改.</p>
 *
 * @param <T> 输入数据类型
 */
@Slf4j
public class SafeBatch<T> implements Iterable<SafeBatch.Batch<T>> {
    private final List<Batch<T>> batches;
    private final GreedyConfig config;

    /**
     * 构造函数.
     *
     * @param allItems 输入数据全集
     * @param config   配置参数
     */
    public SafeBatch(@NonNull List<T> allItems, @NonNull GreedyConfig config) {
        this.config = config;
        List<List<T>> partitionedBatches = Lists.partition(allItems, config.getBatchSize());
        this.batches = new ArrayList<>(partitionedBatches.size());
        for (List<T> batchItems : partitionedBatches) {
            this.batches.add(new Batch<>(batchItems));
        }
    }

    @Override
    @NonNull
    public Iterator<Batch<T>> iterator() {
        return new BatchesIterator<>(batches, config);
    }

    @Getter
    public static class Batch<T> {
        private final List<T> items;

        Batch(List<T> items) {
            this.items = items; // 不进行深拷贝，但使用者要确保原始数据不会被意外修改
        }

        int size() {
            return items.size();
        }

        private int costs() {
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

    private static class BatchesIterator<T> implements Iterator<SafeBatch.Batch<T>> {
        private final Iterator<SafeBatch.Batch<T>> iterator;
        private final GreedyConfig config;
        private int totalItemsProcessed = 0;
        private int totalCosts = 0;

        BatchesIterator(List<SafeBatch.Batch<T>> batches, GreedyConfig config) {
            this.iterator = batches.iterator();
            this.config = config;
        }

        @Override
        public boolean hasNext() {
            return iterator.hasNext();
        }

        @Override
        public SafeBatch.Batch<T> next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }

            final SafeBatch.Batch<T> batch = iterator.next();
            totalItemsProcessed += batch.size();
            if (totalItemsProcessed > config.getItemsLimit()) {
                if (config.getOnItemsLimitExceed() != null) {
                    config.getOnItemsLimitExceed().accept(totalItemsProcessed);
                } else {
                    log.warn("Unsafe batch iteration: {} > {}", totalItemsProcessed, config.getItemsLimit());
                }
            }

            if (config.getRateLimiter() != null) {
                final String key = config.getRateLimiterKey();
                totalCosts += batch.costs();
                if (totalCosts > config.getRateLimitOnCostExceed()) {
                    // 基于成本的有条件限流
                    if (!config.getRateLimiter().canAcquire(key, 1)) {
                        log.warn("Fail to acquire limiter token, totalCosts:{} > {}, itemProcessed:{}, key:{}", totalCosts, config.getRateLimitOnCostExceed(), totalItemsProcessed, key);
                        throw new GreedyException();
                    }
                }
            }
            return batch;
        }
    }
}