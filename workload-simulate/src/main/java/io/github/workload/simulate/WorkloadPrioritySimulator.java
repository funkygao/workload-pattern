package io.github.workload.simulate;

import io.github.workload.WorkloadPriority;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 优先级{@link WorkloadPriority}的模拟生成器，仅用于unit test.
 */
public class WorkloadPrioritySimulator implements Iterable<Map.Entry<WorkloadPriority, Integer>> {
    private final Map<WorkloadPriority, Integer /* 请求数量 */> requests = new HashMap<>();

    /**
     * 完全随机地生成每一种{@link WorkloadPriority}.
     *
     * @param bound 每个{@link WorkloadPriority}的数量上限
     */
    public WorkloadPrioritySimulator simulateFullyRandomWorkloadPriority(int bound) {
        for (int P = 0; P <= WorkloadPriority.MAX_P; P++) {
            requests.put(WorkloadPriority.fromP(P), ThreadLocalRandom.current().nextInt(bound));
        }
        return this;
    }

    /**
     * 生成指定数量的RPC类型{@link WorkloadPriority}.
     *
     * @param N 生成数量
     */
    public WorkloadPrioritySimulator simulateRpcWorkloadPriority(int N) {
        for (int i = 0; i < N; i++) {
            increment(WorkloadPrioritizer.randomRpc());
        }

        return this;
    }

    /**
     * 随机生成指定数量的{@link WorkloadPriority}，按照MQ/Web/RPC/低优先级请求分布.
     *
     * @param N 生成数量
     */
    public WorkloadPrioritySimulator simulateMixedWorkloadPriority(int N) {
        for (int i = 0; i < N; i++) {
            switch (ThreadLocalRandom.current().nextInt(4)) {
                case 0:
                    increment(WorkloadPrioritizer.randomMQ());
                    break;
                case 1:
                    increment(WorkloadPrioritizer.randomWeb());
                    break;
                case 2:
                    increment(WorkloadPrioritizer.randomLowPriority());
                    break;
                default:
                    increment(WorkloadPrioritizer.randomRpc());
                    break;
            }
        }
        return this;
    }

    public WorkloadPrioritySimulator simulateHttpWorkloadPriority(int N) {
        for (int i = 0; i < N; i++) {
            increment(WorkloadPrioritizer.randomWeb());
        }
        return this;
    }

    /**
     * 生成少量的{@link WorkloadPriority}.
     */
    public void simulateFewWorkloadPriority() {
        for (int i = 0; i < 5; i++) {
            int P = ThreadLocalRandom.current().nextInt(WorkloadPriority.MAX_P);
            requests.put(WorkloadPriority.fromP(P), i + 2);
        }
    }

    /**
     * 产生的{@link WorkloadPriority}有多少种.
     */
    public int size() {
        return requests.size();
    }

    public int totalRequests() {
        return requests.values().stream().mapToInt(Integer::intValue).sum();
    }

    public WorkloadPrioritySimulator reset() {
        requests.clear();
        return this;
    }

    private void increment(WorkloadPriority priority) {
        if (!requests.containsKey(priority)) {
            requests.put(priority, 0);
        }
        requests.put(priority, 1 + requests.get(priority));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Iterator<Map.Entry<WorkloadPriority, Integer>> iterator() {
        return requests.entrySet().iterator();
    }
}
