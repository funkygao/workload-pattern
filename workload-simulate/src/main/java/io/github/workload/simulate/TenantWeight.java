package io.github.workload.simulate;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 租户负荷生成权重.
 */
@Data
@AllArgsConstructor
public class TenantWeight {
    /**
     * 租户名称.
     */
    private final String name;

    /**
     * 权重，即该租户要生成多少个 workload.
     */
    private final Integer weight;
}
