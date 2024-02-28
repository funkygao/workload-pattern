package io.github.workload.simulate;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class TenantWorkloadSimulator implements Iterable<String> {
    private final List<String /* tenant name */> tenantWorkloads = new LinkedList<>();

    /**
     * 根据租户权重生成请求数据.
     *
     * @param weights 不同租户的权重计划
     */
    public TenantWorkloadSimulator simulateByWeights(List<TenantWeight> weights) {
        // weighted round robin
        int totalWeight = weights.stream().mapToInt(TenantWeight::getWeight).sum();
        int[] currentWeights = weights.stream().mapToInt(TenantWeight::getWeight).toArray();
        for (int generated = 0; generated < totalWeight; ) {
            for (int i = 0; i < weights.size(); i++) {
                if (currentWeights[i] > 0) {
                    String tenantName = weights.get(i).getName();
                    tenantWorkloads.add(tenantName);

                    currentWeights[i]--;
                    generated++;
                }
            }
        }
        return this;
    }

    /**
     * 模拟生成的总请求量.
     */
    public int totalWorkloads() {
        return tenantWorkloads.size();
    }

    public TenantWorkloadSimulator reset() {
        tenantWorkloads.clear();
        return this;
    }

    /**
     * 返回的是租户名称序列.
     */
    @Override
    public Iterator<String> iterator() {
        return tenantWorkloads.iterator();
    }
}
