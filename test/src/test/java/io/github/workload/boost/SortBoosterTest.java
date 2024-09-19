package io.github.workload.boost;

import ai.djl.ndarray.NDManager;

import org.junit.jupiter.api.Test;

class SortBoosterTest {

    @Test
    void basic() {
        SortBooster sorter = new SortBooster(NDManager.newBaseManager());
        int[] array = new int[]{6, 5, 9, 4, 3, 2, 6, 2, 11, 22, 12, 9};
        sorter.sortArray(0, array);
    }

}