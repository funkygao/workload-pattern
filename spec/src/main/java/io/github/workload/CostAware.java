package io.github.workload;

import io.github.workload.annotations.TestCoverageExcluded;
import lombok.Generated;

import java.io.Serializable;

/**
 * The cost to perform the workload, applied on DTO/any cost calculable class.
 *
 * <p>局限性：该请求本身就可以计算出成本，无外部依赖。</p>
 * <p>不适用这样的场景：请求本身看不出成本，例如：请求就是一个batchNo，它必须从存储中取出该批次下所有数据才能知道成本.</p>
 */
@TestCoverageExcluded
@Generated
public interface CostAware extends Serializable {

    /**
     * 成本.
     *
     * <p>它是抽象的，具体语义由使用者决定.</p>
     */
    int cost();

    /**
     * 标签.
     *
     * <p>它是抽象的，用于细粒度分类等场景.</p>
     */
    default String tag() {
        return null;
    }
}
