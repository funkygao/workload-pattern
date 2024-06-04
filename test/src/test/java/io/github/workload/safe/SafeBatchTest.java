package io.github.workload.safe;

import io.github.workload.BaseTest;
import io.github.workload.greedy.GreedyConfig;
import io.github.workload.greedy.GreedyException;
import io.github.workload.greedy.MockGreedyLimiter;
import io.github.workload.mock.CostAwareDto;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    @Test
    void cost() {
        List<CostAwareDto> items = generateCostAwareDtos(1300, 10);
        GreedyConfig config = GreedyConfig.newDefaultWithLimiter(1000, "cannotAcquire", new MockGreedyLimiter());
        SafeBatch<CostAwareDto> safeBatch = new SafeBatch<>(items, config);
        assertThrows(GreedyException.class, () -> {
            for (SafeBatch.Batch<CostAwareDto> batch : safeBatch) {
            }
        });
    }

    private List<String> generateOrderNos(int n) {
        List<String> orderNos = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            orderNos.add(String.valueOf(i));
        }
        return orderNos;
    }

    private List<CostAwareDto> generateCostAwareDtos(int n, int skuCount) {
        List<CostAwareDto> dtos = new ArrayList<>(n);
        for (long i = 0; i < n; i++) {
            dtos.add(CostAwareDto.create(i, skuCount));
        }
        return dtos;
    }
}