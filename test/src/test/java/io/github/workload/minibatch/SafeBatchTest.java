package io.github.workload.minibatch;

import io.github.workload.BaseTest;
import io.github.workload.greedy.GreedyConfig;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SafeBatchTest extends BaseTest {

    @Test
    void basic() {
        List<String> orderNos = generateOrderNos(120);
        GreedyConfig config = GreedyConfig.newBuilder()
                .batchSize(23)
                .itemsLimit(110)
                .build();
        SafeBatch<String> safeBatch = new SafeBatch<>(orderNos, config);
        int total = 0;
        for (SafeBatch.Batch<String> batch : safeBatch) {
            total += batch.size();
            log.info("{}", batch.getItems());
        }
        assertEquals(120, total);
    }

    private List<String> generateOrderNos(int n) {
        List<String> orderNos = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            orderNos.add(String.valueOf(i));
        }
        return orderNos;
    }

}