package io.github.workload.greedy;

/**
 * A {@link java.util.function.Function} that throws.
 */
@FunctionalInterface
public interface ThrowingFunction<T, R, E extends Throwable> {
    R accept(T t) throws E;
}
