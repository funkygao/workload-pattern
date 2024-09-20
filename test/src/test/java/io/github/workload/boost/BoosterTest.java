package io.github.workload.boost;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

class BoosterTest {

    @Test
    void testConsolidatedShipmentCalculation() {
        int orderCount = 100_000;
        ConsolidatedShipmentCalculator.Order[] orders = generateRandomOrders(orderCount);

        // 使用GPU加速计算集合单
        List<ConsolidatedShipmentCalculator.ConsolidatedShipment> consolidatedShipments =
                ConsolidatedShipmentCalculator.consolidateShipments(orders);

        // 输出结果和执行时间
        System.out.println("Number of Consolidated Shipments: " + consolidatedShipments.size());
    }

    private ConsolidatedShipmentCalculator.Order[] generateRandomOrders(int count) {
        Random random = new Random(42); // 使用固定种子以确保可重复性
        String[] destinations = {"A", "B", "C", "D", "E"}; // 简化的目的地列表
        ConsolidatedShipmentCalculator.Order[] orders = new ConsolidatedShipmentCalculator.Order[count];
        for (int i = 0; i < count; i++) {
            float weight = random.nextFloat() * 100 + 1; // 重量在 1-101 之间
            String destination = destinations[random.nextInt(destinations.length)];
            orders[i] = new ConsolidatedShipmentCalculator.Order(i + 1, weight, destination);
        }
        return orders;
    }

}