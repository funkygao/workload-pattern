package io.github.workload;

/**
 * The cost to perform the workload.
 */
interface WorkloadCostAware {

    /**
     * Normalized cost of the workload.
     *
     * <p>cost is usually expressed in terms of a finite computing resource like CPU, RAM or network bandwidth.</p>
     * <p>In our experience, however, this most usually resolves to CPU, as RAM is often already over-provisioned relative to CPU.</p>
     * <p>Networks can sometimes be the scarce resource, but normally only for specialty cases.</p>
     */
    double cost();
}
