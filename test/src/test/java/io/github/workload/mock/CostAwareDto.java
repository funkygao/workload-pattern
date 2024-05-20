package io.github.workload.mock;

import io.github.workload.CostAware;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class CostAwareDto implements CostAware, Serializable {
    private final Long id;
    private final List<Integer> skus = new ArrayList<>();

    private CostAwareDto(Long id) {
        this.id = id;
    }

    public static CostAwareDto create(Long id, int skuCount) {
        CostAwareDto dto = new CostAwareDto(id);
        for (int i = 0; i < skuCount; i++) {
            dto.skus.add(ThreadLocalRandom.current().nextInt());
        }
        return dto;
    }

    public List<Integer> getSkus() {
        return skus;
    }

    @Override
    public int cost() {
        return skus.size();
    }
}
