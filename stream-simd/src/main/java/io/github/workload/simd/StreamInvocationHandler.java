package io.github.workload.simd;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

class StreamInvocationHandler<T> implements InvocationHandler {
    private static final Set<String> ENHANCEABLE_METHODS = new HashSet<>(Arrays.asList("max", "min", "count"));
    private static final Map<String, Function<long[], Long>> LONG_OPERATIONS = new HashMap<>();

    static {
        LONG_OPERATIONS.put("max", JniStreamEnhancer::findMaxId);
        LONG_OPERATIONS.put("min", JniStreamEnhancer::findMinId);
        LONG_OPERATIONS.put("count", JniStreamEnhancer::countLong);
    }

    private final Stream<T> stream;

    StreamInvocationHandler(Stream<T> stream) {
        this.stream = stream;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        final String methodName = method.getName();
        if (ENHANCEABLE_METHODS.contains(methodName)) {
            Optional<EnhancedOperation> enhancedOp = getEnhancedOperation(methodName);
            if (enhancedOp.isPresent()) {
                return enhancedOp.get().apply(stream);
            }
        }

        // fallback to original stream method
        return method.invoke(stream, args);
    }

    private Optional<EnhancedOperation> getEnhancedOperation(String methodName) {
        Optional<T> first = stream.findFirst();
        if (first.isPresent()) {
            if (first.get() instanceof Long) {
                return getLongStreamOperation(methodName);
            } else if (first.get() instanceof Integer) {
                return getIntStreamOperation(methodName);
            } else if (first.get() instanceof Double) {
                return getDoubleStreamOperation(methodName);
            }
        }

        return Optional.empty();
    }

    private Optional<EnhancedOperation> getLongStreamOperation(String methodName) {
        Function<long[], Long> operation = LONG_OPERATIONS.get(methodName);
        if (operation != null) {
            return Optional.of(s -> operation.apply(toLongArray(s)));
        }
        return Optional.empty();
    }

    private Optional<EnhancedOperation> getIntStreamOperation(String methodName) {
        return Optional.empty();
    }

    private Optional<EnhancedOperation> getDoubleStreamOperation(String methodName) {
        return Optional.empty();
    }

    private long[] toLongArray(Stream<?> s) {
        return s.mapToLong(value -> ((Number) value).longValue()).toArray();
    }

    @FunctionalInterface
    private interface EnhancedOperation extends Function<Stream<?>, Object> {}

}
