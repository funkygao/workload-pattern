package io.github.workload.greedy;

/**
 * A {@link java.util.function.Consumer} that throws.
 */
@FunctionalInterface
public interface ThrowingConsumer<T, E extends Throwable> {
    void accept(T t) throws E;
}
