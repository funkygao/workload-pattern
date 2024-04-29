package io.github.workload.greedy;

import io.github.workload.BaseConcurrentTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GreedySafeInvokerTest extends BaseConcurrentTest {

    private final MockDao dao = new MockDao();
    private final MockKafkaProducer producer = new MockKafkaProducer();
    private final MockRpcApi rpc = new MockRpcApi();

    @Test
    void edge_cases() {
        Exception expected = assertThrows(IllegalArgumentException.class, () -> GreedySafeInvoker.invoke(null, 0, partition -> {
        }, 1));
        assertEquals("partitionSize must be greater than 0", expected.getMessage());
        expected = assertThrows(IllegalArgumentException.class, () -> GreedySafeInvoker.invoke(null, 5, partition -> {
        }, 1));
        assertEquals("greedyThreshold must be greater than partitionSize", expected.getMessage());

        expected = assertThrows(IllegalArgumentException.class, () -> GreedySafeInvoker.invoke(null, 0, partition -> {
            return new ArrayList<MockOrder>();
        }, 12));
        assertEquals("partitionSize must be greater than 0", expected.getMessage());
        expected = assertThrows(IllegalArgumentException.class, () -> GreedySafeInvoker.invoke(null, 4, partition -> {
            return new ArrayList<MockOrder>();
        }, 2));
        assertEquals("greedyThreshold must be greater than partitionSize", expected.getMessage());


        GreedySafeInvoker.invoke(null, 10, partition -> {}, 12);
        GreedySafeInvoker.invoke(new ArrayList<MockOrder>(), 10, partition -> {}, 12);

        List<MockOrder> orders = GreedySafeInvoker.invoke(null, 2, partition -> {
            return new ArrayList<>();
        }, 12);
        assertTrue(orders.isEmpty());
        orders = GreedySafeInvoker.invoke(new ArrayList<>(), 2, partition -> {
            return new ArrayList<>();
        }, 12);
        assertTrue(orders.isEmpty());
    }

    @Test
    void runWithoutResult_happyCase() {
        List<String> orderNos = generateOrderNos(180);
        GreedySafeInvoker.invoke(orderNos, 10, partition -> {
            List<MockOrder> orders = dao.getOrders(partition.getItems());
            log.info("partition id={}", partition.getId());
        }, 120);

    }

    @Test
    void runWithoutResult_illegalArgument() {
        List<String> orderNos = generateOrderNos(180);
        assertThrows(IllegalArgumentException.class, () -> {
            GreedySafeInvoker.invoke(orderNos, 120, partition -> {
            }, 10);
        });
    }

    @Test
    @DisplayName("partitionConsumer使用全集而不是分区后的子集，会抛异常，fail fast，避免数据污染")
    void runWithoutResult_access_whole_set_not_allowed() {
        List<String> orderNos = generateOrderNos(180);
        Exception expected = assertThrows(IllegalStateException.class, () -> {
            GreedySafeInvoker.invoke(orderNos, 10, partition -> {
                //dao.getOrders(partition.getItems());
                dao.getOrders(orderNos);
            }, 11);
        });
        assertEquals("BUG! Partition<String> not accessed, accessing the whole dataset?", expected.getMessage());
    }

    @Test
    @DisplayName("演示分批发送kafka消息")
    void demo_kafka_batch_send() throws Exception {
        List<String> orderNos = generateOrderNos(123);
        GreedySafeInvoker.invoke(orderNos, 100, partition -> {
            List<MockOrder> orders = dao.getOrders(partition.getItems());
            producer.sendMessage(orders);
        }, 1000);
    }

    @Test
    @DisplayName("演示批量调用RPC")
    void demo_rpc_batch_call() {
        List<String> orderNos = generateOrderNos(30);
        List<MockOrder> orders = GreedySafeInvoker.invoke(orderNos, 30, partition -> {
            return rpc.fetchOrders(partition.getItems());
        }, 50);
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