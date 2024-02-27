package io.github.workload.shufflesharding;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

class CardDealerKafkaPartitioner {

    @RepeatedTest(1)
    @DisplayName("按照用户id分配Kafka分区")
    void forKafkaProducePartitioner() {
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

    private int partition(CardDealer dealer, String uid) {
        int hand[] = new int[dealer.getHandSize()];
        dealer.dealIntoHand(uid, hand);
        int i = ThreadLocalRandom.current().nextInt(hand.length);
        return hand[i];
    }
}
