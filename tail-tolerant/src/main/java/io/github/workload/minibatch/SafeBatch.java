package io.github.workload.minibatch;

import com.google.common.collect.Lists;
import io.github.workload.greedy.GreedyConfig;
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
    public static class Batch<IN> {
        private final List<IN> items;

        Batch(List<IN> items) {
            this.items = items; // 不进行深拷贝，但使用者要确保原始数据不会被意外修改
        }

        public int size() {
            return items.size();
        }
    }

    private static class BatchesIterator<IN> implements Iterator<SafeBatch.Batch<IN>> {
        private final Iterator<SafeBatch.Batch<IN>> iterator;
        private final GreedyConfig config;
        private int totalItemsProcessed = 0;

        BatchesIterator(List<SafeBatch.Batch<IN>> batches, GreedyConfig config) {
            this.iterator = batches.iterator();
            this.config = config;
        }

        @Override
        public boolean hasNext() {
            return iterator.hasNext();
        }

        @Override
        public SafeBatch.Batch<IN> next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }

            final SafeBatch.Batch<IN> next = iterator.next();
            totalItemsProcessed += next.size();
            if (totalItemsProcessed > config.getItemsLimit()) {
                if (config.getOnItemsLimitExceed() != null) {
                    config.getOnItemsLimitExceed().accept(totalItemsProcessed);
                } else {
                    log.warn("Unsafe batch iteration: {} > {}", totalItemsProcessed, config.getItemsLimit());
                }
            }
            return next;
        }
    }
}