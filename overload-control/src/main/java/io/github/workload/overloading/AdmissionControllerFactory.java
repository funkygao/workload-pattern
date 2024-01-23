package io.github.workload.overloading;

import io.github.workload.annotations.VisibleForTesting;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Slf4j
class AdmissionControllerFactory {
    // within JVM, instances can be for rpc/http/mq/worker/etc
    private static final Map<String, AdmissionController> instances = new ConcurrentHashMap<>(8);

    static <T extends AdmissionController> T getInstance(@NonNull String name, @NonNull Supplier<T> supplier) {
        // https://github.com/apache/shardingsphere/pull/13275/files
        // https://bugs.openjdk.org/browse/JDK-8161372
        AdmissionController instance = instances.get(name);
        if (instance != null) {
            return (T) instance;
        }

        return (T) instances.computeIfAbsent(name, key -> {
            log.info("register new admission controller:{}", name);
            return supplier.get();
        });
    }

    @VisibleForTesting("清除共享的静态变量，以便隔离单元测试")
    static void resetForTesting() {
        instances.clear();
    }

}
