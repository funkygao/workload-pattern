package io.github.workload.safe;

import io.github.workload.BaseTest;
import io.github.workload.mock.CostAwareDto;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class SafeBatcherTest extends BaseTest {

    @Test
    void null_guard() {
        List<String> orderNos = generateOrderNos(121);
        SafeBatcher<String> safeLoader = new SafeBatcher<>(orderNos, 28, null);
        int total = 0;
        for (SafeBatcher.Batch<String> batch : safeLoader) {
            total += batch.size();
        }
        assertEquals(121, total);
    }

    @Test
    void validate() {
        SafeBatcher.Guard guard = SafeBatcher.Guard.builder().itemsThreshold(50).build();
        assertThrows(IllegalArgumentException.class, () -> new SafeBatcher<>(generateOrderNos(10), 100, guard));
    }

    @Test
    void basic() {
        List<String> orderNos = generateOrderNos(120);
        SafeBatcher.Guard guard = SafeBatcher.Guard.builder()
                .itemsThreshold(110)
                .build();
        SafeBatcher<String> safeLoader = new SafeBatcher<>(orderNos, 23, guard);
        int total = 0;
        for (SafeBatcher.Batch<String> batch : safeLoader) {
            total += batch.size();
            log.info("{}", batch.getItems());
        }
        assertEquals(120, total);
    }

    @Test
    void forEach() {
        List<String> orderNos = generateOrderNos(120);
        SafeBatcher.Guard guard = SafeBatcher.Guard.builder()
                .itemsThreshold(110)
                .build();
        SafeBatcher<String> safeLoader = new SafeBatcher<>(orderNos, 23, guard);
        AtomicInteger total = new AtomicInteger();
        safeLoader.forEach(batch -> {
            log.info("forEach: {}", batch.getItems());
            total.addAndGet(batch.size());
        });
        assertEquals(120, total.get());
    }

    @Test
    void real_use_case() {
        List<String> orderNos = generateOrderNos(100);
        SafeBatcher<String> safeBatcher = new SafeBatcher<>(orderNos, 50, SafeBatcher.Guard.builder().itemsThreshold(200).build());
        for (SafeBatcher.Batch<String> batch : safeBatcher) {
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