package io.github.workload.simd;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.stream.Stream;

class StreamInvocationHandler<T> implements InvocationHandler {
    private final Stream<T> stream;

    StreamInvocationHandler(Stream<T> stream) {
        this.stream = stream;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.getName().equals("max")) {
            Optional<Stream<T>> longStream = getStreamOfLong();
            if (longStream.isPresent()) {
                return JniStreamEnhancer.findMaxId(
                        longStream.get().mapToLong(value -> (Long) value).toArray()
                );
            }
        }

        // fallback
        return method.invoke(stream, args);
    }

    private Optional<Stream<T>> getStreamOfLong() {
        Optional<T> first = stream.findFirst();
        if (first.isPresent() && first.get() instanceof Long) {
            return Optional.of(Stream.concat(Stream.of(first.get()), stream));
        }
        return Optional.empty();
    }
}
