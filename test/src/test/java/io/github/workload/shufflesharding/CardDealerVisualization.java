package io.github.workload.shufflesharding;

import org.junit.jupiter.api.Test;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

class CardDealerVisualization {
    private static int nodes = 10;
    private static int handSize = 2;
    private static int dbShards = 4;
    private static int users = 20;
    private StringBuilder d2 = new StringBuilder();
    private Map<Integer, List<Integer>> user2nodes = new TreeMap<>();
    private Map<Integer, Set<Integer>> node2db = new HashMap<>();
    private static Map<Integer, Integer> user2db = new HashMap<>();
    
    @Test
    void visualizeCluster() throws IOException {
        CardDealer dealer = CardDealer.builder().deckSize(nodes).handSize(handSize).build();
        int[] hands = new int[handSize];
        for (int uid = 0; uid < users; uid++) {
            dealer.dealIntoHand(String.valueOf(uid), hands);
            if (!user2nodes.containsKey(uid)) {
                user2nodes.put(uid, new ArrayList<>());
            }
            for (int node : hands) {
                // 该用户被该节点服务
                user2nodes.get(uid).add(node);
                if (!node2db.containsKey(node)) {
                    node2db.put(node, new HashSet<>());
                }
                // 该节点连接该数据库分片
                node2db.get(node).add(user2db.get(uid));
            }
        }

        for (int uid : user2nodes.keySet()) {
            for (int node : user2nodes.get(uid)) {
                addLine(String.format("U%d -> Node%d", uid, node));
            }
        }
        for (int node : node2db.keySet()) {
            for (int db : node2db.get(node)) {
                addLine(String.format("Node%d -> DB%d", node, db));
            }
        }

        dumpToFile("../doc/shufflesharding.d2", d2.toString());
    }

    void dumpToFile(String path, String content) throws IOException {
        File file = new File(path);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.append(content);
        }
    }

    static {
        for (int uid = 0; uid < users; uid++) {
            user2db.put(uid, uid % dbShards);
        }
    }

    private void addLine(String content) {
        d2.append(content).append(System.getProperty("line.separator"));
    }
}
