package io.github.workload.boost;

import ai.djl.Device;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.types.Shape;

public class SortBooster {

    public static int[] sortArray(NDManager manager, int[] array) {
        // CPU拷贝到GPU
        NDArray ndArray = manager.create(array, new Shape(-1)).toDevice(Device.fromName("0"), true);
        // // GPU内排序
        NDArray sortedArray = ndArray.sort(0);
        // GPU拷贝回CPU
        return sortedArray.toIntArray();
    }
}
