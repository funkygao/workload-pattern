package io.github.workload.greedy;

import io.github.workload.CostAware;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 大报文(贪婪工作负荷)安全的分批处理大数据集的工具类，控制方法内(无法使用AOP)的大报文风险.
 *
 * <p>支持有返回值和无返回值的数据集处理场景.</p>
 *
 * <ul>Features:
 * <li>避免{@code Lists.partition()}后容易误把全集变量应用在子集处理代码的问题</li>
 * <li>对总遍历次数审计：超过{@link GreedyConfig#greedyThreshold}时执行{@link GreedyConfig#thresholdExceededAction}</li>
 * <li>基于大报文成本的限流：成本超过{@link GreedyConfig#costsThreshold}会触发限流器{@link GreedyConfig#greedyLimiter}，被限流时抛出{@link GreedyException}</li>
 * </ul>
 */
@UtilityClass
@Slf4j
@Deprecated
public class GreedySafe {

    /**
     * 处理大数据集分批任务的方法，无返回值场景.
     *
     * @param items         要处理的数据集
     * @param config        大报文保护的配置
     * @param batchConsumer 处理每一个({@link Batch})数据处理者
     * @param <IN>          输入数据类型
     * @param <E>           异常类型
     * @throws E 如果在处理数据时发生异常
     */
    public <IN, E extends Throwable> void scatter(List<IN> items, GreedyConfig config, @NonNull GreedySafe.BatchConsumer<Batch<IN>, E> batchConsumer) throws E {
        processItems(items, config.getBatchSize(), batch -> {
            batchConsumer.accept(batch);
            return null;
        }, config, false);
    }

    /**
     * {@link #scatter(List, GreedyConfig, BatchConsumer)} with default config.
     */
    public <IN, E extends Throwable> void scatter(List<IN> items, @NonNull GreedySafe.BatchConsumer<Batch<IN>, E> batchConsumer) throws E {
        scatter(items, GreedyConfig.newDefault(), batchConsumer);
    }

    /**
     * 处理大数据集分批任务的方法，有返回值场景.
     *
     * @param items         要处理的数据集
     * @param config        大报文保护的配置
     * @param batchFunction 处理每一个({@link Batch})数据处理者
     * @param <OUT>         结果数据类型
     * @param <IN>          输入数据类型
     * @param <E>           异常类型
     * @return 结果集
     * @throws E 如果在处理数据时发生异常
     */
    public <OUT, IN, E extends Throwable> List<OUT> scatterGather(List<IN> items, GreedyConfig config, @NonNull GreedySafe.BatchFunction<Batch<IN>, List<OUT>, E> batchFunction) throws E {
        return processItems(items, config.getBatchSize(), batchFunction, config, true);
    }

    /**
     * {@link #scatterGather(List, GreedyConfig, BatchFunction)} with default config.
     */
    public <OUT, IN, E extends Throwable> List<OUT> scatterGather(List<IN> items, @NonNull GreedySafe.BatchFunction<Batch<IN>, List<OUT>, E> batchFunction) throws E {
        return scatterGather(items, GreedyConfig.newDefault(), batchFunction);
    }

    private <OUT, IN, E extends Throwable> List<OUT> processItems(List<IN> items, int batchSize, BatchFunction<Batch<IN>, List<OUT>, E> processor, GreedyConfig config, boolean hasResult) throws E {
        if (items == null || items.isEmpty()) {
            return hasResult ? new ArrayList<>() : null;
        }

        List<OUT> results = null;
        if (hasResult) {
            results = new ArrayList<>(items.size());
        }
        int itemsProcessed = 0;
        int costs = 0;
        for (int start = 0, batchId = 0; start < items.size(); start += batchSize, batchId++) {
            final int end = Math.min(items.size(), start + batchSize);
            List<IN> batchData = items.subList(start, end);
            Batch<IN> batch = new Batch<>(batchData, batchId);
            List<OUT> batchResult = processor.apply(batch);
            if (results != null && batchResult != null) {
                results.addAll(batchResult);
            }

            itemsProcessed += batchData.size();
            if (!batch.accessed) {
                // fail fast to avoid escaping to production environment
                throw new IllegalStateException("BUG! Batch not accessed, accessing the whole dataset?");
            }

            if (config.getRateLimiter() != null) {
                final String key = config.getRateLimiterKey();
                costs += batch.costs();
                if (costs > config.getRateLimitOnCostExceed()) {
                    if (!config.getRateLimiter().canAcquire(key, 1)) {
                        log.warn("Fail to acquire limiter token, accCost:{} > {}, itemProcessed:{}, key:{}", costs, config.getRateLimitOnCostExceed(), itemsProcessed, key);
                        throw new GreedyException();
                    }
                }
            }
        }

        if (itemsProcessed > config.getItemsLimit()) {
            Consumer<Integer> thresholdExceededAction = config.getOnItemsLimitExceed();
            if (thresholdExceededAction == null) {
                log.warn("Items processed exceed threshold: {} > {}", itemsProcessed, config.getItemsLimit());
            } else {
                thresholdExceededAction.accept(itemsProcessed);
            }
        }

        return results;
    }

    @RequiredArgsConstructor
    public class Batch<T> {
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

    @FunctionalInterface
    public interface BatchFunction<T, R, E extends Throwable> {
        R apply(T t) throws E;
    }

    @FunctionalInterface
    public interface BatchConsumer<T, E extends Throwable> {
        void accept(T t) throws E;
    }
}
