package io.github.workload.overloading;

/**
 * Value smoother to prevent phenomenon burrs.
 */
interface ValueSmoother {

    ValueSmoother update(double newValue);

    double smoothedValue();
}
