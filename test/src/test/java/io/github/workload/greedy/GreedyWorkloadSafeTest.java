package io.github.workload.greedy;

import io.github.workload.BaseConcurrentTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;

class GreedyWorkloadSafeTest extends BaseConcurrentTest {

    private final MockDao dao = new MockDao();
    private final MockKafkaProducer producer = new MockKafkaProducer();
    private final MockRpcApi rpc = new MockRpcApi();

    @Test
    void runWithoutResult_normalCase() {
        List<String> orderNos = generateOrderNos(180);
        GreedyWorkloadSafe.runWithoutResult(orderNos, 10, partition -> {
            List<MockOrder> orders = dao.getOrders(partition.getItems());
            log.info("partition id={}", partition.getId());
        }, 120);

    }

    @Test
    void runWithoutResult_illegalArguemnt() {
        List<String> orderNos = generateOrderNos(180);
        assertThrows(IllegalArgumentException.class, () -> {
            GreedyWorkloadSafe.runWithoutResult(orderNos, 120, partition -> {
            }, 10);
        });
    }

    @Test
    @DisplayName("partitionConsumer使用全集而不是分区后的子集，会抛异常，fail fast，避免数据污染")
    void runWithoutResult_access_whole_set_not_allowed() {
        List<String> orderNos = generateOrderNos(180);
        assertThrows(IllegalStateException.class, () -> {
            GreedyWorkloadSafe.runWithoutResult(orderNos, 10, partition -> {
                //dao.getOrders(partition.getItems());
                dao.getOrders(orderNos);
            }, 11);
        });
    }

    @Test
    @DisplayName("演示分批发送kafka消息")
    void demo_kafka_batch_send() {
        List<String> orderNos = generateOrderNos(180);
        GreedyWorkloadSafe.runWithoutResult(orderNos, 100, partition -> {
            List<MockOrder> orders = dao.getOrders(partition.getItems());
            producer.sendMessage(orders);
        }, 1000);
    }

    private List<String> generateOrderNos(int n) {
        List<String> orderNos = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            orderNos.add(String.valueOf(i));
        }
        return orderNos;
    }

}