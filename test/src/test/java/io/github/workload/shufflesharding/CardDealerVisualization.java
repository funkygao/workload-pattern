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
                addLine(String.format("Node%d -> U%d {", node, uid));
                addLine(String.format("  style.stroke: %s", colors[node % colors.length]));
                addLine(" style.stroke-width: 5");
                addLine("}");
            }
        }
        for (int uid = 0; uid < users; uid++) {
            addLine(String.format("U%d -> DB%d", uid, uid % dbShards));
        }
        dumpToFile("../doc/shufflesharding.d2", d2.toString());
    }

    void dumpToFile(String path, String content) throws IOException {
        File file = new File(path);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.append(content);
        }
    }


    private static String[] colors = new String[]{
            "aqua",
            "aquamarine",
            "black",
            "blue",
            "blueviolet", "brown", "burlywood", "cadetblue", "chartreuse",
            "chocolate", "coral", "cornflowerblue", "cornsilk", "crimson",
            "cyan", "darkblue", "darkcyan", "darkgoldenrod", "darkgray",
            "darkgreen", "darkgrey", "darkkhaki", "darkmagenta", "darkolivegreen",
            "darkorange", "darkorchid", "darkred", "darksalmon", "darkseagreen",
            "darkslateblue", "darkslategray", "darkslategrey", "darkturquoise", "darkviolet",
            "deeppink", "deepskyblue", "dimgray", "dimgrey", "dodgerblue",
            "firebrick", "floralwhite", "forestgreen", "fuchsia", "gainsboro",
            "ghostwhite", "gold", "goldenrod", "gray", "green",
            "greenyellow", "grey", "honeydew", "hotpink", "indianred",
            "indigo", "ivory", "khaki", "lavender", "lavenderblush",
            "lawngreen", "lemonchiffon", "lightblue", "lightcoral", "lightcyan",
            "lightgoldenrodyellow", "lightgray", "lightgreen", "lightgrey", "lightpink",
            "lightsalmon", "lightseagreen", "lightskyblue", "lightslategray", "lightslategrey",
            "lightsteelblue", "lightyellow", "lime", "limegreen", "linen",
            "magenta", "maroon", "mediumaquamarine", "mediumblue", "mediumorchid",
            "mediumpurple", "mediumseagreen", "mediumslateblue", "mediumspringgreen", "mediumturquoise",
            "mediumvioletred", "midnightblue", "mintcream", "mistyrose", "moccasin",
            "navajowhite", "navy", "oldlace", "olive", "olivedrab",
            "orange", "orangered", "orchid", "palegoldenrod", "palegreen",
            "paleturquoise", "palevioletred", "papayawhip", "peachpuff", "peru",
            "pink", "plum", "powderblue", "purple", "rebeccapurple",
            "red", "rosybrown", "royalblue", "saddlebrown", "salmon",
            "sandybrown", "seagreen", "seashell", "sienna", "silver",
            "skyblue", "slateblue", "slategray", "slategrey", "snow",
            "springgreen", "steelblue", "tan", "teal", "thistle",
            "tomato", "turquoise", "violet", "wheat", "white",
            "whitesmoke", "yellow", "yellowgreen"
    };

    private void addLine(String content) {
        d2.append(content).append(System.getProperty("line.separator"));
    }
}
