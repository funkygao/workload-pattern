package io.github.workload.tail.agent;

import java.util.ArrayList;
import java.util.List;

public class AgentTestEntryPoint {

    public static void main(String[] args) {
        System.out.println("agent test main");

        List<String> list = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            list.add(String.valueOf(i + 1));
        }

        for (String i : list) {

        }
        list.stream().forEach(System.out::println);
    }
}
