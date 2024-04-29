package io.github.workload.greedy;

import java.util.ArrayList;
import java.util.List;

class MockRpcApi {

    List<MockOrder> fetchOrders(List<String> orderNos) {
        List<MockOrder> orders = new ArrayList<>(orderNos.size());
        for (String orderNo : orderNos) {
            orders.add(new MockOrder(Integer.valueOf(orderNo)));
        }
        return orders;
    }

}
