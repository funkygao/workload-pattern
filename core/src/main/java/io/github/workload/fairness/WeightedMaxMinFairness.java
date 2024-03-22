package io.github.workload.fairness;

import io.github.workload.annotations.PoC;
import lombok.Generated;
import lombok.experimental.UtilityClass;

/**
 * WMMF，加权最大最小公平分配算法.
 *
 * <p>n个用户共享总带宽c，每个用户对带宽的需求不同，如何把c分配给每个用户?</p>
 * <p>应用示例：Kafka的某个topic有c个partition，消息有n种优先级，每种优先级的消息量不同，如何分配partition给不同的优先级?</p>
 * <p>与first-come first-served相比，a flow with bursts of many packets will only punish itself and not other flows.</p>
 */
@UtilityClass
@PoC
@Generated
class WeightedMaxMinFairness {

}
