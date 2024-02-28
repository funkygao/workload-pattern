package io.github.workload.shufflesharding;

import io.github.workload.simulate.TenantWeight;
import io.github.workload.simulate.TenantWorkloadSimulator;
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

import static org.junit.jupiter.api.Assertions.assertEquals;

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

        List<TenantWeight> plans = Stream.of(
                new TenantWeight("a", 20),
                new TenantWeight("b", 6000),
                new TenantWeight("c", 30),
                new TenantWeight("d", 590),
                new TenantWeight("e", 5),
                new TenantWeight("f", 120)
        ).collect(Collectors.toList());
        TenantWorkloadSimulator simulator = new TenantWorkloadSimulator();
        simulator.simulateByWeights(plans);

        Map<Integer, List<String>> allocated = new TreeMap<>();
        for (String tenantName : simulator) {
            final int p = partition(dealer, tenantName);
            List<String> messagesInPartition = allocated.get(p);
            if (messagesInPartition == null) {
                messagesInPartition = new LinkedList<>();
                allocated.put(p, messagesInPartition);
            }
            messagesInPartition.add(tenantName);
        }

        int total = 0;
        for (Integer partition : allocated.keySet()) {
            List<String> messages = allocated.get(partition);
            System.out.printf("%2d %4d %s\n", partition, messages.size(), messages);
            total += messages.size();
        }
        assertEquals(total, simulator.totalWorkloads());
    }

    private int partition(CardDealer dealer, String uid) {
        int hand[] = new int[dealer.getHandSize()];
        dealer.dealIntoHand(uid, hand);
        int i = ThreadLocalRandom.current().nextInt(hand.length);
        return hand[i];
    }

}
