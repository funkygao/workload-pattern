package io.github.workload.shufflesharding;

import lombok.Data;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class CardDealerKafkaPartitioner {

    @RepeatedTest(1)
    @DisplayName("按照用户id分配Kafka分区")
    void kafkaProducePartitionerByUID() {
        final int partitions = 32;
        final int handSize = 3;
        CardDealer dealer = CardDealer.builder()
                .deckSize(partitions)
                .handSize(handSize)
                .build();
        Map<Integer, AtomicInteger> histogram = new ConcurrentHashMap<>();
        for (int id = 1; id < 1 << 20; id++) {
            String uid = String.valueOf(id);
            int p = partition(dealer, uid);
            AtomicInteger counter = histogram.computeIfAbsent(p, key -> new AtomicInteger());
            counter.incrementAndGet();
        }

        int max = Integer.MIN_VALUE;
        int min = Integer.MAX_VALUE;
        for (Integer partition : histogram.keySet()) {
            final int n = histogram.get(partition).get();
            if (n > max) {
                max = n;
            }
            if (n < min) {
                min = n;
            }

            System.out.printf("%2d %d\n", partition, n);
        }
        System.out.println(max - min);
    }

    @RepeatedTest(1)
    void kafkaPartitionerByTenant() {
        final int partitions = 32;
        final int handSize = 3;
        CardDealer dealer = CardDealer.builder()
                .deckSize(partitions)
                .handSize(handSize)
                .build();

        List<TenantProducer> producers = Stream.of(
                new TenantProducer("a", 20),
                new TenantProducer("b", 6000),
                new TenantProducer("c", 30),
                new TenantProducer("d", 590),
                new TenantProducer("e", 5),
                new TenantProducer("f", 120)
        ).collect(Collectors.toList());

        Map<Integer, List<String>> allocated = new TreeMap<>();
        int totalWeight = producers.stream().mapToInt(TenantProducer::getWeight).sum();
        int[] currentWeights = producers.stream().mapToInt(TenantProducer::getWeight).toArray();
        for (int produced = 0; produced < totalWeight;) {
            for (int i = 0; i < producers.size(); i++) {
                if (currentWeights[i] > 0) {
                    String tenantName = producers.get(i).name;

                    final int p = partition(dealer, tenantName);
                    List<String> messagesInPartition = allocated.get(p);
                    if (messagesInPartition == null) {
                        messagesInPartition = new LinkedList<>();
                        allocated.put(p, messagesInPartition);
                    }
                    messagesInPartition.add(tenantName);

                    currentWeights[i]--;
                    produced++;
                }
            }
        }
        for (Integer partition : allocated.keySet()) {
            List<String> messages = allocated.get(partition);
            System.out.printf("%d %d\n", partition, messages.size());
            System.out.println(messages);
        }
    }

    private int partition(CardDealer dealer, String uid) {
        int hand[] = new int[dealer.getHandSize()];
        dealer.dealIntoHand(uid, hand);
        int i = ThreadLocalRandom.current().nextInt(hand.length);
        return hand[i];
    }

    @Data
    private static class TenantProducer {
        String name;
        int weight; // 权重，即该租户要发送多少条消息

        TenantProducer(String name, int weight) {
            this.name = name;
            this.weight = weight;
        }

        int getWeight() {
            return weight;
        }
    }
}
