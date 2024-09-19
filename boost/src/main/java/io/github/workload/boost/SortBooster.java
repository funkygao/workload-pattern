package io.github.workload.boost;

import ai.djl.Device;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.types.Shape;
import lombok.NonNull;

/**
 * Sorting with GPU booster.
 */
public class SortBooster {
    private static final int BATCH_THRESHOLD = 5000;

    private final NDManager manager;

    public SortBooster(@NonNull NDManager manager) {
        this.manager = manager;
    }

    /**
     * 利用GPU加速排序：大报文场景的排序.
     *
     * @param gpuDeviceId GPU设备ID
     * @param array       Java数组
     * @return 排序后的数组
     */
    public int[] sortArray(int gpuDeviceId, int[] array) {
        if (!hasGPU(gpuDeviceId) || degradeToCPU(array.length)) {
            // has no GPU, or batch size not big enough TODO
        }

        // CPU拷贝到GPU
        NDArray ndArray = manager.create(array, new Shape(-1)).toDevice(Device.gpu(gpuDeviceId), false);
        // // GPU内排序
        NDArray sortedArray = ndArray.sort(0);
        // GPU拷贝回CPU
        return sortedArray.toIntArray();
    }

    private boolean degradeToCPU(int size) {
        return size <= BATCH_THRESHOLD;
    }

    private boolean hasGPU(int gpuDeviceId) {
        return gpuDeviceId > -1;
    }
}
