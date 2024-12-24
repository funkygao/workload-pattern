package io.github.workload.simd;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StreamEnhancerTest {

    @BeforeAll
    static void setUp() {
        // 获取 stream-simd 模块的 target/classes 目录
        String streamSimdClassesDir = new File("../stream-simd/target/classes").getAbsolutePath();
        System.setProperty("java.library.path", streamSimdClassesDir);

        // 强制 JVM 重新加载库路径
        try {
            Field field = ClassLoader.class.getDeclaredField("sys_paths");
            field.setAccessible(true);
            field.set(null, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    void basic() {
        List<Long> numbers = Arrays.asList(10L, 5L, 15L, 20L, 3L, 8L, 25L);

        long maxId = StreamEnhancer.enhance(numbers.stream())
                .mapToLong(Long::longValue)
                .max()
                .orElse(0L);
        assertEquals(25L, maxId);
    }

}