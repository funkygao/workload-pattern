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
                partition -> {
                }));
        assertEquals("partitionSize must be greater than 0", expected.getMessage());
        expected = assertThrows(IllegalArgumentException.class, () -> GreedySafe.scatter(null,
                GreedyConfig.newBuilder().batchSize(5).itemsLimit(1).build(),
                partition -> {
                }));
        assertEquals("greedyThreshold must be greater than partitionSize", expected.getMessage());

        expected = assertThrows(IllegalArgumentException.class, () -> GreedySafe.scatterGather(null,
                GreedyConfig.newBuilder().batchSize(0).itemsLimit(12).build(),
                partition -> {
                    return new ArrayList<MockOrder>();
                }));
        assertEquals("partitionSize must be greater than 0", expected.getMessage());
        expected = assertThrows(IllegalArgumentException.class, () -> GreedySafe.scatterGather(null,
                GreedyConfig.newBuilder().batchSize(4).itemsLimit(2).build(),
                partition -> {
                    return new ArrayList<MockOrder>();
                }));
        assertEquals("greedyThreshold must be greater than partitionSize", expected.getMessage());


        GreedySafe.scatter(null,
                GreedyConfig.newBuilder().batchSize(10).itemsLimit(12).build(),
                partition -> {
                });
        GreedySafe.scatter(new ArrayList<MockOrder>(),
                GreedyConfig.newBuilder().batchSize(10).itemsLimit(12).build(),
                partition -> {
                });

        List<MockOrder> orders = GreedySafe.scatterGather(null,
                GreedyConfig.newBuilder().batchSize(2).itemsLimit(12).build(),
                partition -> {
                    return new ArrayList<>(partition.getItems().size());
                });
        assertTrue(orders.isEmpty());
        orders = GreedySafe.scatterGather(new ArrayList<>(),
                GreedyConfig.newBuilder().batchSize(2).itemsLimit(12).build(),
                partition -> {
                    return new ArrayList<>();
                });
        assertTrue(orders.isEmpty());
    }

    @Test
    void runWithoutResult_happyCase() {
        List<String> orderNos = generateOrderNos(180);
        GreedySafe.scatter(orderNos,
                GreedyConfig.newBuilder().batchSize(10).itemsLimit(120).build(),
                partition -> {
                    List<MockOrder> orders = dao.getOrders(partition.getItems());
                    log.info("partition id={}", partition.getId());
                });

    }

    @Test
    @DisplayName("partitionConsumer使用全集而不是分区后的子集，会抛异常，fail fast，避免数据污染")
    void runWithoutResult_access_whole_set_not_allowed() {
        List<String> orderNos = generateOrderNos(180);
        Exception expected = assertThrows(IllegalStateException.class, () -> {
            GreedySafe.scatter(orderNos,
                    GreedyConfig.newBuilder().batchSize(10).itemsLimit(120).build(),
                    partition -> {
                        //dao.getOrders(partition.getItems());
                        dao.getOrders(orderNos);
                    });
        });
        assertEquals("BUG! Partition not accessed, accessing the whole dataset?", expected.getMessage());
    }

    @Test
    void scatter_with_default() {
        Exception expected = assertThrows(IllegalStateException.class, () -> GreedySafe.scatter(generateOrderNos(1200), partition -> {
        }));
        assertEquals("BUG! Partition not accessed, accessing the whole dataset?", expected.getMessage());

        GreedySafe.scatter(generateOrderNos(1200), stringPartition -> {
            log.info("{}", stringPartition.getItems());
        });

        assertThrows(GreedyException.class, () -> GreedySafe.scatter(generateCostAwareDtos(1300, 10),
                GreedyConfig.newDefaultWithLimiter(1000, "cannotAcquire", new MockGreedyLimiter()),
                partition -> {
                    partition.getItems();
                }));
        GreedySafe.scatter(generateCostAwareDtos(1300, 10),
                GreedyConfig.newDefaultWithLimiter(1000, "ok", new MockGreedyLimiter()),
                partition -> {
                    partition.getItems();
                });
    }

    @Test
    void scatterGather_with_default() {
        List<Integer> result = GreedySafe.scatterGather(generateCostAwareDtos(125, 2), costAwareDtoPartition -> {
            List<Integer> r = new ArrayList<>();
            for (CostAwareDto dto : costAwareDtoPartition.getItems()) {
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
                partition -> {
                    List<MockOrder> orders = dao.getOrders(partition.getItems());
                    producer.sendMessage(orders);
                });
    }

    @Test
    @DisplayName("演示批量调用RPC")
    void demo_rpc_batch_call() {
        List<String> orderNos = generateOrderNos(30);
        List<MockOrder> orders = GreedySafe.scatterGather(orderNos,
                GreedyConfig.newBuilder().batchSize(30).itemsLimit(1000).build(),
                partition -> {
                    return rpc.fetchOrders(partition.getItems());
                });
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