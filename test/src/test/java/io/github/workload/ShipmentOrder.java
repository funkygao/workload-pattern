package io.github.workload;

import lombok.Data;

import java.util.List;

@Data
public class ShipmentOrder implements CostAware {
    private String orderNo;
    private String merchantNo;
    private String ownerNo;
    private List<String> items;

    @Override
    public int cost() {
        return items.size();
    }

    @Override
    public String tag() {
        return ownerNo;
    }
}
