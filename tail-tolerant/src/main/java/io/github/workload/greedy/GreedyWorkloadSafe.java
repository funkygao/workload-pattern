package io.github.workload.greedy;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.experimental.UtilityClass;

import java.util.List;

@UtilityClass
public final class GreedyWorkloadSafe {

    public static <IN, E extends Throwable> void runWithoutResult(@NonNull List<IN> items, int partitionSize, ThrowingConsumer<Partition<IN>, E> partitionConsumer, int greedyThreshold) {

    }

    public static <OUT, IN, E extends Throwable> List<OUT> runWithResult(@NonNull List<IN> items, int partitionSize, ThrowingFunction<Partition<IN>, List<OUT>, E> partitionFunction, Class<OUT> resultClazz, int greedyThreshold) {
        if (greedyThreshold < partitionSize) {
            throw new IllegalArgumentException("");
        }

        return null;
    }

    @RequiredArgsConstructor
    public static class Partition<T> {
        private final List<T> items;
        @Getter
        private final int id;
        private boolean accessed = false;

        public List<T> getItems() {
            accessed = true;
            return items;
        }
    }
}
