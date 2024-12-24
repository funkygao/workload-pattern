package io.github.workload.simd;

import java.util.stream.Stream;

import static java.lang.reflect.Proxy.newProxyInstance;

public class StreamSIMDEnhancer {

    /**
     * 封装 {@link Stream}，底层通过{@code SIMD}加速，上层透明.
     */
    @SuppressWarnings("unchecked")
    public static <T> Stream<T> simdEnhance(Stream<T> stream) {
        return (Stream<T>) newProxyInstance(
                Stream.class.getClassLoader(),
                new Class<?>[]{Stream.class},
                new StreamInvocationHandler<>(stream)
        );
    }
}
