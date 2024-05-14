package io.github.workload.greedy;

import io.github.workload.BaseConcurrentTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GreedySafeTest extends BaseConcurrentTest {

    private final MockDao dao = new MockDao();
    private final MockKafkaProducer producer = new MockKafkaProducer();
    private final MockRpcApi rpc = new MockRpcApi();

    @Test
    void edge_cases() {
        Exception expected = assertThrows(IllegalArgumentException.class, () -> GreedySafe.scatter(null,
                GreedyConfig.newBuilder().partitionSize(0).greedyThreshold(1).build(),
                partition -> {
                }));
        assertEquals("partitionSize must be greater than 0", expected.getMessage());
        expected = assertThrows(IllegalArgumentException.class, () -> GreedySafe.scatter(null,
                GreedyConfig.newBuilder().partitionSize(5).greedyThreshold(1).build(),
                partition -> {
                }));
        assertEquals("greedyThreshold must be greater than partitionSize", expected.getMessage());

        expected = assertThrows(IllegalArgumentException.class, () -> GreedySafe.scatterGather(null,
                GreedyConfig.newBuilder().partitionSize(0).greedyThreshold(12).build(),
                partition -> {
                    return new ArrayList<MockOrder>();
                }));
        assertEquals("partitionSize must be greater than 0", expected.getMessage());
        expected = assertThrows(IllegalArgumentException.class, () -> GreedySafe.scatterGather(null,
                GreedyConfig.newBuilder().partitionSize(4).greedyThreshold(2).build(),
                partition -> {
                    return new ArrayList<MockOrder>();
                }));
        assertEquals("greedyThreshold must be greater than partitionSize", expected.getMessage());


        GreedySafe.scatter(null,
                GreedyConfig.newBuilder().partitionSize(10).greedyThreshold(12).build(),
                partition -> {
                });
        GreedySafe.scatter(new ArrayList<MockOrder>(),
                GreedyConfig.newBuilder().partitionSize(10).greedyThreshold(12).build(),
                partition -> {
                });

        List<MockOrder> orders = GreedySafe.scatterGather(null,
                GreedyConfig.newBuilder().partitionSize(2).greedyThreshold(12).build(),
                partition -> {
                    return new ArrayList<>(partition.getItems().size());
                });
        assertTrue(orders.isEmpty());
        orders = GreedySafe.scatterGather(new ArrayList<>(),
                GreedyConfig.newBuilder().partitionSize(2).greedyThreshold(12).build(),
                partition -> {
                    return new ArrayList<>();
                });
        assertTrue(orders.isEmpty());
    }

    @Test
    void runWithoutResult_happyCase() {
        List<String> orderNos = generateOrderNos(180);
        GreedySafe.scatter(orderNos,
                GreedyConfig.newBuilder().partitionSize(10).greedyThreshold(120).build(),
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
                    GreedyConfig.newBuilder().partitionSize(10).greedyThreshold(120).build(),
                    partition -> {
                        //dao.getOrders(partition.getItems());
                        dao.getOrders(orderNos);
                    });
        });
        assertEquals("BUG! Partition not accessed, accessing the whole dataset?", expected.getMessage());
    }

    @Test
    @DisplayName("演示分批发送kafka消息")
    void demo_kafka_batch_send() throws Exception {
        List<String> orderNos = generateOrderNos(123);
        GreedySafe.scatter(orderNos,
                GreedyConfig.newBuilder().partitionSize(100).greedyThreshold(120).build(),
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
                GreedyConfig.newBuilder().partitionSize(30).greedyThreshold(1000).build(),
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

}