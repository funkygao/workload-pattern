package io.github.workload.smoother;

/**
 * Value smoother to prevent phenomenon burrs.
 */
public interface ValueSmoother {

    /**
     * 接收新的原始值，并更新内部平滑值.
     */
    ValueSmoother update(double newValue);

    /**
     * 平滑后的值.
     */
    double smoothedValue();
}
