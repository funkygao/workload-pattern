package io.github.workload.greedy;

@FunctionalInterface
public interface ThrowingFunction<T, R, E extends Throwable> {
    R accept(T t) throws E;
}
