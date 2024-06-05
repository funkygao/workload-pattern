package io.github.workload.safe;

import io.github.workload.BaseTest;
import io.github.workload.mock.CostAwareDto;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SafeBatchLoaderTest extends BaseTest {

    @Test
    void null_safeCost() {
        List<String> orderNos = generateOrderNos(121);
        SafeBatchLoader<String> safeLoader = new SafeBatchLoader<>(orderNos, 28, null);
        int total = 0;
        for (SafeBatchLoader.Batch<String> batch : safeLoader) {
            total += batch.size();
        }
        assertEquals(121, total);
    }

    @Test
    void validate() {
        SafeBatchLoader.Guard guard = SafeBatchLoader.Guard.builder().itemsThreshold(50).build();
        assertThrows(IllegalArgumentException.class, () -> new SafeBatchLoader<>(generateOrderNos(10), 100, guard));
    }

    @Test
    void basic() {
        List<String> orderNos = generateOrderNos(120);
        SafeBatchLoader.Guard guard = SafeBatchLoader.Guard.builder()
                .itemsThreshold(110)
                .build();
        SafeBatchLoader<String> safeLoader = new SafeBatchLoader<>(orderNos, 23, guard);
        int total = 0;
        for (SafeBatchLoader.Batch<String> batch : safeLoader) {
            total += batch.size();
            log.info("{}", batch.getItems());
        }
        assertEquals(120, total);
    }

    @Test
    void real_use_case() {
        List<String> orderNos = generateOrderNos(100);
        SafeBatchLoader<String> safeBatchLoader = new SafeBatchLoader<>(orderNos, 50, SafeBatchLoader.Guard.builder().itemsThreshold(200).build());
        for (SafeBatchLoader.Batch<String> batch : safeBatchLoader) {
            rpcCall(batch.getItems(), "com.foo.service.Foo.getOrders");
            rpcCall(batch.first(), "com.bar.OrderService.freeze");
        }
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

    private void rpcCall(Object arg, String method) {
        log.info("calling {}({})", method, arg);
    }
}