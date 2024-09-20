package io.github.workload.boost;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

class BoosterTest {

    @Test
    @DisplayName("为10万个发货单创建集合单：任务分配")
    void demoBoost() {
        // 模拟创建10万个发货单
        ConsolidatedShipmentCalculator.Order[] orders = generateRandomOrders(100_000);

        // 使用GPU加速计算集合单分配
        List<ConsolidatedShipmentCalculator.ConsolidatedShipment> consolidatedShipments =
                ConsolidatedShipmentCalculator.consolidateShipments(orders);

        System.out.println("Number of Consolidated Shipments: " + consolidatedShipments.size());
    }

    private ConsolidatedShipmentCalculator.Order[] generateRandomOrders(int count) {
        Random random = new Random(42); // 使用固定种子以确保可重复性
        String[] destinations = {"A", "B", "C", "D", "E"}; // 简化的目的地列表
        ConsolidatedShipmentCalculator.Order[] orders = new ConsolidatedShipmentCalculator.Order[count];
        for (int i = 0; i < count; i++) {
            float weight = random.nextFloat() * 100 + 1; // 重量在 1-101 之间
            String destination = destinations[random.nextInt(destinations.length)];
            orders[i] = new ConsolidatedShipmentCalculator.Order(i, weight, destination);
        }
        return orders;
    }
}
