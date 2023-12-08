package io.github.workload.overloading;

import io.github.workload.annotations.Immutable;
import lombok.Getter;

@Getter
@Immutable
class WorkloadSheddingPolicy {

    /**
     * 降速因子.
     */
    private double dropRate = 0.05; // 5%

    /**
     * 提速因子.
     *
     * <p>(加速下降/reject request，慢速恢复/admit request)</p>
     * <p>相当于冷却周期，如果没有它会造成负载短时间下降造成大量请求被放行，严重时打满CPU</p>
     */
    private double recoverRate = 0.01; // 1%
}
