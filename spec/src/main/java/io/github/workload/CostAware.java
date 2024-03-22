package io.github.workload;

import io.github.workload.annotations.TestCoverageExcluded;
import lombok.Generated;

import java.io.Serializable;

/**
 * The cost to perform the workload, applied on DTO.
 *
 * <p>Cost is usually expressed in terms of a finite computing resource like CPU, RAM or network bandwidth.</p>
 * <p>In our experience, however, this most usually resolves to CPU, as RAM is often already over-provisioned relative to CPU.</p>
 * <p>Networks can sometimes be the scarce resource, but normally only for specialty cases.</p>
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
