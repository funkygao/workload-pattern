package io.github.workload.overloading;

/**
 * CPU饱和器.
 *
 * <ul>simply using CPU consumption as the signal for provisioning works well:
 * <li>In platforms with garbage collection, memory pressure naturally translates into increased CPU consumption</li>
 * <li>In other platforms, it's possible to provision the remaining resources in such a way that they're very unlikely to run out before CPU runs out</li>
 * </ul>
 */
public interface CpuSaturator {

    /**
     * CPU负荷是否已饱和?
     *
     * <p>过载保护体系通常在一个窗口期内只调用本方法一次.</p>
     */
    boolean saturated();
}
