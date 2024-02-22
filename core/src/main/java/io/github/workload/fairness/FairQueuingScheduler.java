package io.github.workload.fairness;

import java.util.*;

/**
 * 完全公平调度算法.
 *
 * <p>与FIFO/Priority queue相比的优势：a high-data-rate flow cannot take more than its fair share of the link capacity</p>
 *
 * @see <a href="https://github.com/kubernetes/apiserver/tree/master/pkg/util/flowcontrol/fairqueuing/queueset">K8S fairqueuing应用于workload</a>
 */
class FairQueuingScheduler {
    private Map<String, Queue<Packet>> flowQueues;
    private List<String> activeFlows;
    private int time = 0;

    public FairQueuingScheduler() {
        flowQueues = new HashMap<>();
        activeFlows = new ArrayList<>();
    }

    public void addPacket(String flowId, Packet packet) {
        flowQueues.computeIfAbsent(flowId, k -> new LinkedList<>()).add(packet);
        if (!activeFlows.contains(flowId)) {
            activeFlows.add(flowId);
        }
    }

    public void schedule() {
        while (!activeFlows.isEmpty()) {
            Iterator<String> it = activeFlows.iterator();
            while (it.hasNext()) {
                String flowId = it.next();
                Queue<Packet> queue = flowQueues.get(flowId);
                if (queue.isEmpty()) {
                    it.remove();
                    continue;
                }

                Packet packet = queue.poll();
                processPacket(flowId, packet, time++);

                if (queue.isEmpty()) {
                    it.remove();
                }
            }
        }
    }

    private void processPacket(String flowId, Packet packet, int time) {
        System.out.println("Time: " + time + ", Flow: " + flowId + ", Processed: " + packet);
    }

    public static void main(String[] args) {
        FairQueuingScheduler scheduler = new FairQueuingScheduler();

        // Add some packets to different flows
        scheduler.addPacket("Flow1", new Packet("Packet1"));
        scheduler.addPacket("Flow2", new Packet("Packet2"));
        scheduler.addPacket("Flow1", new Packet("Packet3"));
        scheduler.addPacket("Flow3", new Packet("Packet4"));
        scheduler.addPacket("Flow2", new Packet("Packet5"));

        // Run the scheduler
        scheduler.schedule();
    }
}

class Packet {
    private String name;

    public Packet(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }
}
