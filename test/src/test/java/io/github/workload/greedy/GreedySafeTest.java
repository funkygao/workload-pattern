package io.github.workload.greedy;

import io.github.workload.BaseTest;
import io.github.workload.mock.CostAwareDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GreedySafeTest extends BaseTest {

    private final MockDao dao = new MockDao();
    private final MockKafkaProducer producer = new MockKafkaProducer();
    private final MockRpcApi rpc = new MockRpcApi();

    @Test
    void edge_cases() {
        Exception expected = assertThrows(IllegalArgumentException.class, () -> GreedySafe.scatter(null,
                GreedyConfig.newBuilder().batchSize(0).itemsLimit(1).build(),
                batch -> {
                }));
        assertEquals("batchSize must be greater than 0", expected.getMessage());
        expected = assertThrows(IllegalArgumentException.class, () -> GreedySafe.scatter(null,
                GreedyConfig.newBuilder().batchSize(5).itemsLimit(1).build(),
                batch -> {
                }));
        assertEquals("itemsLimit must be greater than batchSize", expected.getMessage());

        expected = assertThrows(IllegalArgumentException.class, () -> GreedySafe.scatterGather(null,
                GreedyConfig.newBuilder().batchSize(0).itemsLimit(12).build(),
                batch -> {
                    return new ArrayList<MockOrder>();
                }));
        assertEquals("batchSize must be greater than 0", expected.getMessage());
        expected = assertThrows(IllegalArgumentException.class, () -> GreedySafe.scatterGather(null,
                GreedyConfig.newBuilder().batchSize(4).itemsLimit(2).build(),
                batch -> {
                    return new ArrayList<MockOrder>();
                }));
        assertEquals("itemsLimit must be greater than batchSize", expected.getMessage());


        GreedySafe.scatter(null,
                GreedyConfig.newBuilder().batchSize(10).itemsLimit(12).build(),
                batch -> {
                });
        GreedySafe.scatter(new ArrayList<MockOrder>(),
                GreedyConfig.newBuilder().batchSize(10).itemsLimit(12).build(),
                batch -> {
                });

        List<MockOrder> orders = GreedySafe.scatterGather(null,
                GreedyConfig.newBuilder().batchSize(2).itemsLimit(12).build(),
                batch -> {
                    return new ArrayList<>(batch.getItems().size());
                });
        assertTrue(orders.isEmpty());
        orders = GreedySafe.scatterGather(new ArrayList<>(),
                GreedyConfig.newBuilder().batchSize(2).itemsLimit(12).build(),
                batch -> {
                    return new ArrayList<>();
                });
        assertTrue(orders.isEmpty());
    }

    @Test
    void runWithoutResult_happyCase() {
        List<String> orderNos = generateOrderNos(180);
        GreedySafe.scatter(orderNos,
                GreedyConfig.newBuilder().batchSize(10).itemsLimit(120).build(),
                batch -> {
                    List<MockOrder> orders = dao.getOrders(batch.getItems());
                    log.info("batch id={}", batch.getId());
                });

    }

    @Test
    @DisplayName("使用全集而不是分区后的子集，会抛异常，fail fast，避免数据污染")
    void runWithoutResult_access_whole_set_not_allowed() {
        List<String> orderNos = generateOrderNos(180);
        Exception expected = assertThrows(IllegalStateException.class, () -> {
            GreedySafe.scatter(orderNos,
                    GreedyConfig.newBuilder().batchSize(10).itemsLimit(120).build(),
                    batch -> {
                        //dao.getOrders(batch.getItems());
                        dao.getOrders(orderNos);
                    });
        });
        assertEquals("BUG! Batch not accessed, accessing the whole dataset?", expected.getMessage());
    }

    @Test
    void scatter_with_default() {
        Exception expected = assertThrows(IllegalStateException.class, () -> GreedySafe.scatter(generateOrderNos(1200), batch -> {
        }));
        assertEquals("BUG! Batch not accessed, accessing the whole dataset?", expected.getMessage());

        GreedySafe.scatter(generateOrderNos(1200), batch -> {
            log.info("{}", batch.getItems());
        });

        assertThrows(GreedyException.class, () -> GreedySafe.scatter(generateCostAwareDtos(1300, 10),
                GreedyConfig.newDefaultWithLimiter(1000, "cannotAcquire", new MockGreedyLimiter()),
                batch -> {
                    batch.getItems();
                }));
        GreedySafe.scatter(generateCostAwareDtos(1300, 10),
                GreedyConfig.newDefaultWithLimiter(1000, "ok", new MockGreedyLimiter()),
                batch -> {
                    batch.getItems();
                });
    }

    @Test
    void scatterGather_with_default() {
        List<Integer> result = GreedySafe.scatterGather(generateCostAwareDtos(125, 2), batch -> {
            List<Integer> r = new ArrayList<>();
            for (CostAwareDto dto : batch.getItems()) {
                r.add(dto.cost());
            }
            return r;
        });
        assertEquals(125, result.size());
        assertEquals(125 * 2, result.stream().reduce(Integer::sum).get());
    }

    @Test
    @DisplayName("演示分批发送kafka消息")
    void demo_kafka_batch_send() throws Exception {
        List<String> orderNos = generateOrderNos(123);
        GreedySafe.scatter(orderNos,
                GreedyConfig.newBuilder().batchSize(100).itemsLimit(120).build(),
                batch -> {
                    List<MockOrder> orders = dao.getOrders(batch.getItems());
                    producer.sendMessage(orders);
                });
    }

    @Test
    @DisplayName("演示批量调用RPC")
    void demo_rpc_batch_call() {
        List<String> orderNos = generateOrderNos(30);
        List<MockOrder> orders = GreedySafe.scatterGather(orderNos,
                GreedyConfig.newBuilder().batchSize(30).itemsLimit(1000).build(),
                batch -> rpc.fetchOrders(batch.getItems()));
        assertEquals(30, orders.size());
        assertEquals(1, orders.get(1).getId());
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
