package io.github.workload.greedy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

class MockDao {
    private final AtomicInteger id = new AtomicInteger(0);

    List<MockOrder> getOrders(List<String> orderNos) {
        List<MockOrder> orders = new ArrayList<>(orderNos.size());
        for (int i = 0; i < orderNos.size(); i++) {
            orders.add(new MockOrder(id.incrementAndGet()));
        }
        return orders;
    }

    void insertOrders(List<MockOrder> orders) {

    }
}
