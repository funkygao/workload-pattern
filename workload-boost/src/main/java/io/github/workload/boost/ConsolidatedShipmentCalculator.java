package io.github.workload.boost;

import java.util.ArrayList;
import java.util.List;

/**
 * 集合单分配.
 */
class ConsolidatedShipmentCalculator {
    private static final float MAX_WEIGHT = 500; // 最大重量限制

    /**
     * 发货单.
     */
    public static class Order {
        public int id;
        public float weight;
        public String destination;

        public Order(int id, float weight, String destination) {
            this.id = id;
            this.weight = weight;
            this.destination = destination;
        }
    }

    /**
     * 被合并的发货单.
     */
    public static class ConsolidatedShipment {
        public List<Order> orders;
        public float totalWeight;
        public String destination;

        public ConsolidatedShipment(String destination) {
            this.orders = new ArrayList<>();
            this.totalWeight = 0;
            this.destination = destination;
        }

        public void addOrder(Order order) {
            orders.add(order);
            totalWeight += order.weight;
        }
    }

    /**
     * 把原始发货单进行合并.
     */
    public static List<ConsolidatedShipment> consolidateShipments(Order[] orders) {
        Integer[] consolidatedIndices = new Integer[orders.length];

        Booster.boost(orders, consolidatedIndices, order -> {
            int consolidatedIndex = order.id;
            float currentWeight = order.weight;

            for (int j = 0; j < orders.length; j++) {
                if (order.id != j && orders[j].destination.equals(order.destination)) {
                    float combinedWeight = currentWeight + orders[j].weight;
                    if (combinedWeight <= MAX_WEIGHT) {
                        currentWeight = combinedWeight;
                        consolidatedIndex = Math.min(consolidatedIndex, j);
                    }
                }
            }

            return consolidatedIndex;
        });

        return createConsolidatedShipments(orders, consolidatedIndices);
    }

    private static List<ConsolidatedShipment> createConsolidatedShipments(Order[] orders, Integer[] consolidatedIndices) {
        List<ConsolidatedShipment> shipments = new ArrayList<>();
        boolean[] processed = new boolean[orders.length];

        for (int i = 0; i < orders.length; i++) {
            if (!processed[i]) {
                ConsolidatedShipment shipment = new ConsolidatedShipment(orders[i].destination);
                for (int j = 0; j < orders.length; j++) {
                    if (consolidatedIndices[j].equals(consolidatedIndices[i]) && !processed[j]) {
                        shipment.addOrder(orders[j]);
                        processed[j] = true;
                    }
                }
                shipments.add(shipment);
            }
        }

        return shipments;
    }

}
