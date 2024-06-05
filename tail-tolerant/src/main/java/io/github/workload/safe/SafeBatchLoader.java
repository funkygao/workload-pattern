package io.github.workload.safe;

import com.google.common.collect.Lists;
import io.github.workload.CostAware;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

/**
 * 大报文安全的大数据集分批加载器：抑制方法内(无法使用AOP)的大报文风险.
 *
 * <p>NOTE：使用者要确保原始数据不会被意外修改.</p>
 * <ul>Features:
 * <li>避免不经意的大报文过度遍历</li>
 * <li>基于成本的条件式限流</li>
 * </ul>
 *
 * @param <T> 数据集的数据类型
 */
@Slf4j
public class SafeBatchLoader<T> implements Iterable<SafeBatchLoader.Batch<T>> {
    private final List<Batch<T>> batches;
    private final Guard guard;

    /**
     * 构造函数.
     *
     * @param dataset   输入数据全集
     * @param batchSize size of each partitioned list
     * @param guard     大报文安全保护参数, null 表示不加保护仅仅分批处理
     */
    public SafeBatchLoader(@NonNull List<T> dataset, int batchSize, Guard guard) {
        validate(batchSize, guard);

        this.guard = guard;
        List<List<T>> partitionedBatches = Lists.partition(dataset, batchSize);
        this.batches = partitionedBatches.stream()
                .map(Batch::new)
                .collect(Collectors.toList());
    }

    private void validate(int batchSize, Guard guard) {
        if (guard == null) {
            return;
        }

        if (guard.getItemsThreshold() < batchSize) {
            throw new IllegalArgumentException("unsafeItemsThreshold must be greater than batchSize");
        }
    }

    @Override
    @NonNull
    public Iterator<Batch<T>> iterator() {
        return new BatchesIterator<>(batches, guard);
    }

    @Getter
    public static class Batch<T> {
        private final List<T> items;

        Batch(List<T> items) {
            this.items = items; // 不进行深拷贝，但使用者要确保原始数据不会被意外修改
        }

        T first() {
            return items.get(0);
        }

        int size() {
            return items.size();
        }

        private int costs() {
            int costs = 0;
            if (items != null && !items.isEmpty() && items.get(0) instanceof CostAware) {
                for (T item : items) {
                    costs += ((CostAware) item).cost();
                }
            }
            return costs;
        }
    }

    private static class BatchesIterator<T> implements Iterator<Batch<T>> {
        private final Iterator<Batch<T>> iterator;
        private final Guard guard;
        private int totalItemsProcessed = 0;
        private int totalCosts = 0;

        BatchesIterator(List<Batch<T>> batches, Guard guard) {
            this.iterator = batches.iterator();
            this.guard = guard;
        }

        @Override
        public boolean hasNext() {
            return iterator.hasNext();
        }

        @Override
        public Batch<T> next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }

            final Batch<T> batch = iterator.next();
            if (guard == null) {
                // 不加保护
                return batch;
            }

            totalItemsProcessed += batch.size();
            if (totalItemsProcessed > guard.getItemsThreshold()) {
                log.warn("Unsafe items threshold reached: {} > {}", totalItemsProcessed, guard.getItemsThreshold());
            }
            return batch;
        }
    }

    @Getter
    @Builder
    public static class Guard {
        private final int itemsThreshold;
        private final int costsThreshold;
    }
}