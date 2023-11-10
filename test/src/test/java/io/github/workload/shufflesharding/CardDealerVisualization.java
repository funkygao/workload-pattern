package io.github.workload.shufflesharding;

import org.junit.jupiter.api.Test;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

class CardDealerVisualization {
    private static final String NEWLINE = System.getProperty("line.separator");

    private int nodes = 10;
    private int handSize = 2;
    private int dbShards = 4;
    private int users = 20;

    private StringBuilder d2 = new StringBuilder();
    private Map<Integer, List<Integer>> node2user = new TreeMap<>();

    @Test
    void visualizeCluster() throws IOException {
        CardDealer dealer = CardDealer.builder().deckSize(nodes).handSize(handSize).build();
        int[] hands = new int[handSize];
        for (int uid = 0; uid < users; uid++) {
            dealer.dealIntoHand(String.valueOf(uid), hands);
            for (int node : hands) {
                if (!node2user.containsKey(node)) {
                    node2user.put(node, new ArrayList<>());
                }
                node2user.get(node).add(uid);
            }
        }
        for (int node : node2user.keySet()) {
            List<Integer> uidList = node2user.get(node);
            for (int uid : uidList) {
                d2.append(String.format("Node%d -> U%d", node, uid)).append(NEWLINE);
            }
        }
        for (int uid = 0; uid < users; uid++) {
            d2.append(String.format("U%d -> DB%d", uid, uid % dbShards)).append(NEWLINE);
        }
        dumpToFile("../doc/shufflesharding.d2", d2.toString());
    }

    void dumpToFile(String path, String content) throws IOException {
        File file = new File(path);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.append(content);
        }
    }
}
