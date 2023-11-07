package io.github.workload.shufflesharding;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class CardDealerTest {

    @Test
    void builder() {
        CardDealer.builder().deckSize(10).handSize(4).build();
        try {
            CardDealer.builder().build();
            fail();
        } catch (IllegalArgumentException expected) {
            assertEquals("deckSize or handSize is not positive", expected.getMessage());
        }
    }

    @Test
    void constructor() {
        try {
            new CardDealer(2, 10);
            fail();
        } catch (IllegalArgumentException expected) {
            assertEquals("handSize is greater than deckSize", expected.getMessage());
        }

        try {
            new CardDealer(0, 0);
            fail();
        } catch (IllegalArgumentException expected) {
            assertEquals("deckSize or handSize is not positive", expected.getMessage());
        }

        try {
            int deckSize = 1 << 27; // 1亿多，我们最多支持6千万
            new CardDealer(deckSize, 3);
            fail();
        } catch (IllegalArgumentException expected) {
            assertEquals("deckSize is impractically large", expected.getMessage());
        }

        try {
            new CardDealer(1, 0);
            fail();
        } catch (IllegalArgumentException expected) {
            assertEquals("deckSize or handSize is not positive", expected.getMessage());
        }

        try {
            new CardDealer(0, 1);
            fail();
        } catch (IllegalArgumentException expected) {
            assertEquals("deckSize or handSize is not positive", expected.getMessage());
        }

        try {
            new CardDealer(-1, 0);
            fail();
        } catch (IllegalArgumentException expected) {
            assertEquals("deckSize or handSize is not positive", expected.getMessage());
        }
        try {
            new CardDealer(512, 8);
            fail();
        } catch (IllegalArgumentException expected) {
            assertEquals("required entropy bits of deckSize 512 and handSize 8 is greater than 60", expected.getMessage());
        }

        // ok
        new CardDealer(10, 2); // 2 out of 10 nodes
        new CardDealer(8, 1);
        new CardDealer(2, 1);
        new CardDealer(3, 1);
        new CardDealer(3, 2);
        new CardDealer(8, 4);
        new CardDealer(20, 6);
    }

    @Test
    void requiredEntropyBits() {
        assertEquals(60, CardDealer.requiredEntropyBits(1024, 6));
        assertEquals(72, CardDealer.requiredEntropyBits(512, 8));
        assertEquals(40, CardDealer.requiredEntropyBits(32, 8));
        assertEquals(30, CardDealer.requiredEntropyBits(32, 6));
        assertEquals(20, CardDealer.requiredEntropyBits(32, 4));
        assertEquals(60, CardDealer.requiredEntropyBits(32, 12));
        for (int deckSize = 4; deckSize < 400; deckSize += 2) {
            // step=2：我们的服务器都是部署在2 az，所以总节点数都是偶数，租户分配节点数也是偶数
            for (int handSize = 2; handSize <= deckSize && handSize < CardDealer.MaxHashBits; handSize += 2) {
                int entropyBits = CardDealer.requiredEntropyBits(deckSize, handSize);
                if (entropyBits > CardDealer.MaxHashBits && false) {
                    System.out.printf("d=%d, h=%d %d\n", deckSize, handSize, CardDealer.requiredEntropyBits(deckSize, handSize));
                    break;
                }
            }
        }
    }

    @Test
    void dealIntoHandWithIdentifier() {
        CardDealer dealer = CardDealer.builder().deckSize(10).handSize(4).build();
        int[] hand = new int[4];
        try {
            dealer.dealIntoHand(null, hand);
            fail();
        } catch (IllegalArgumentException expected) {
            assertEquals("identifier cannot be null", expected.getMessage());
        }

        dealer.dealIntoHand("6_616", hand);
        assertArrayEquals(new int[]{6, 0, 3, 5}, hand);
        dealer.dealIntoHand("6_618", hand);
        assertArrayEquals(new int[]{6, 5, 0, 1}, hand);
        dealer.dealIntoHand("8_318", hand);
        assertArrayEquals(new int[]{1, 2, 7, 6}, hand);
    }

    @Test
    void testHashing() {
        String[] identifiers = new String[]{"6_123", "7_123", "7_8987"};
        long[] hashes = new long[]{-7821791131662840031L, 4555407234204538993L, 3681362237365477764L};
        for (int i = 0; i < identifiers.length; i++) {
            assertEquals(hashes[i], CardDealer.hash(identifiers[i]));
        }
    }

    @Test
    void dealByMethodName() {
        CardDealer dealer = new CardDealer(8, 3); // 3 out of 8 nodes
        int[] hand = new int[3];
        dealer.dealIntoHand("SkuService.querySku".hashCode(), hand);
        assertArrayEquals(new int[]{3, 0, 2}, hand);
        dealer.dealIntoHand("SkuService.listSku".hashCode(), hand);
        assertArrayEquals(new int[]{1, 5, 7}, hand);
        dealer.dealIntoHand(-12122222, hand); // hashValue为负
        assertArrayEquals(new int[]{2, 7, 3}, hand);
        dealer.dealIntoHand(0, hand); // hashValue为0
        assertArrayEquals(new int[]{0, 1, 2}, hand);
        dealer.dealIntoHand(1, hand); // hashValue为1
        assertArrayEquals(new int[]{1, 0, 2}, hand);

        try {
            dealer.dealIntoHand("x".hashCode(), null);
        } catch (IllegalArgumentException expected) {
            assertEquals("hand cannot be null and must have length of handSize", expected.getMessage());
        }

        try {
            dealer.dealIntoHand("x".hashCode(), new int[2]);
        } catch (IllegalArgumentException expected) {
            assertEquals("hand cannot be null and must have length of handSize", expected.getMessage());
        }
    }

    @Test
    void dealByWarehouseNo() {
        CardDealer dealer = new CardDealer(8, 4);
        final String warehouseNo1 = "6_616";
        final String warehouseNo2 = "6_6_605";
        int[] hand = new int[4];
        int[] hand1 = new int[4];
        dealer.dealIntoHand(warehouseNo1.hashCode(), hand);
        dealer.dealIntoHand("6_616".hashCode(), hand1);
        assertArrayEquals(hand, hand1); // 同一个仓，为它分配的节点是稳定不变的
        assertArrayEquals(new int[]{2, 1, 3, 0}, hand);
        dealer.dealIntoHand(warehouseNo2.hashCode(), hand);
        assertArrayEquals(new int[]{1, 5, 2, 0}, hand); // 这2个仓分配的node集合不重叠

        List<int[]> hands = new ArrayList<>();
        int N = 2000;
        for (int w = 1; w < N; w++) {
            String warehouseNo = "25_" + w;
            CardDealer dealer1 = CardDealer.builder().deckSize(8).handSize(4).build();
            int[] hand2 = new int[4];
            dealer1.dealIntoHand(warehouseNo.hashCode(), hand2);
            hands.add(hand2);
            if (true) {
                System.out.printf("%7s: ", warehouseNo);
                for (int h : hand2) {
                    System.out.printf("%d ", h);
                }
                System.out.println();
            }
        }

        // assure minimal overlaps
        Set<int[]> set = new HashSet<>();
        for (int[] h : hands) {
            set.add(h);
        }
        assertEquals(N - 1, set.size()); // 经测试，发现有1个重复项
    }

    @Test
    void duplication() {
        int[] deckSizes = new int[]{4, 8, 10, 12, 128, 256, 512};
        int[] handSizes = new int[]{4, 8, 10, 12, 8, 7, 6};
        Random random = new Random();
        for (int loop = 0; loop < 1 << 16; loop++) {
            for (int i = 0; i < deckSizes.length; i++) {
                int deckSize = deckSizes[i];
                int handSize = handSizes[i];
                int hashValue = random.nextInt() & Integer.MAX_VALUE;
                CardDealer dealer = CardDealer.builder().deckSize(deckSize).handSize(handSize).build();
                int[] hand = new int[handSize];
                // with hashValue
                dealer.dealIntoHand(hashValue, hand);
                Set<Integer> cardSet = new HashSet<>();
                for (int j = 0; j < hand.length; j++) {
                    int card = hand[j];
                    // card取值范围合法
                    if (card < 0 || card >= deckSize) {
                        fail();
                    }
                    cardSet.add(card);
                }
                // 不重复
                assertEquals(handSize, cardSet.size());

                // with identifier
                String identifier = "w" + i;
                dealer.dealIntoHand(identifier, hand);
                cardSet.clear();
                for (int j = 0; j < hand.length; j++) {
                    int card = hand[j];
                    // card取值范围合法
                    if (card < 0 || card >= deckSize) {
                        fail();
                    }
                    cardSet.add(card);
                }
                // 不重复
                assertEquals(handSize, cardSet.size());
            }
        }
    }

    @Test
    void sameIdentifierDiffHandSize() {
        int handSize = 3;
        CardDealer dealer1 = CardDealer.builder().deckSize(10).handSize(handSize).build();
        int[] hand1 = new int[handSize];
        dealer1.dealIntoHand("SkuQueryService.getSku", hand1);
        handSize = 3;
        CardDealer dealer2 = CardDealer.builder().deckSize(10).handSize(handSize).build();
        int[] hand2 = new int[handSize];
        dealer2.dealIntoHand("SkuQueryService.getSku", hand2);
        for (int i = 0; i < 3; i++) {
            assertEquals(hand1[i], hand2[i]);
        }
    }

}
