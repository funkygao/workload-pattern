package io.github.workload.overloading;

/**
 * Value smoother for phenomenon burr.
 */
interface ValueSmoother {

    ValueSmoother update(double newValue);

    double smoothedValue();
}
