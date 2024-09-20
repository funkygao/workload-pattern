package io.github.workload.boost;

import com.aparapi.Kernel;
import com.aparapi.Range;
import com.aparapi.device.Device;
import com.aparapi.internal.kernel.KernelManager;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Function;

/**
 * 利用 GPU 进行计算加速.
 *
 * <p>使用 Aparapi 库来将 Java 代码转换为 OpenCL，从而在 GPU 上执行。</p>
 */
@Slf4j
public class Booster {
    private static final boolean USE_GPU = isGPUAvailable();

    /**
     * 对输入数组进行处理，将结果存储在输出数组中(只支持单卡)。
     * 如果 GPU 不可用，将自动降级到 CPU 计算。
     *
     * @param <T>       输入数组元素类型
     * @param <R>       输出数组元素类型
     * @param input     输入数组
     * @param output    输出数组
     * @param operation 要应用于每个元素的操作
     * @return 处理后的输出数组
     */
    public static <T, R> R[] boost(T[] input, R[] output, Function<T, R> operation) {
        if (USE_GPU) {
            return gpuParallelProcess(input, output, operation);
        } else {
            return cpuParallelProcess(input, output, operation);
        }
    }

    private static <T, R> R[] gpuParallelProcess(T[] input, R[] output, Function<T, R> operation) {
        Kernel kernel = new Kernel() {
            @Override
            public void run() {
                // Aparapi 会尝试将 run 里的 Java 代码转换为 OpenCL 代码：成功就在GPU执行，否则回退到CPU
                // run 其实就是要被转换为 内核函数 的 Java 原始代码，有限制：不能使用对象方法调用、新建对象等复杂操作

                // GPU 采用的是 SIMT：single instruction multiple threads
                // 通过 global id 来确定线程该处理哪个数据，它与 Range 对应
                int i = getGlobalId();
                // 在 GPU 上执行操作：Aparapi 自动处理 Java 到 OpenCL 的转换
                // 每个 GPU 线程处理输入数组的一个元素，应用给定的操作，并将结果存储在输出数组中
                output[i] = operation.apply(input[i]);
            }
        };

        try {
            // 创建与输入数组大小相同的范围，确保每个数组元素都有一个对应的 GPU 线程
            Range range = Range.create(input.length);
            // 自动搬运数据：内核执行前，数据从 Java 堆复制到 GPU 内存；内核执行后，结果从 GPU 内存复制回 Java 堆
            kernel.execute(range);
        } catch (Exception e) {
            log.warn("GPU execution failed, falling back to CPU: " + e.getMessage());
            return cpuParallelProcess(input, output, operation);
        } finally {
            kernel.dispose();
        }

        return output;
    }

    private static <T, R> R[] cpuParallelProcess(T[] input, R[] output, Function<T, R> operation) {
        for (int i = 0; i < input.length; i++) {
            output[i] = operation.apply(input[i]);
        }
        return output;
    }

    private static boolean isGPUAvailable() {
        try {
            Device device = KernelManager.instance().bestDevice();
            return device != null && device.getType() == Device.TYPE.GPU;
        } catch (Exception e) {
            return false;
        }
    }
}
