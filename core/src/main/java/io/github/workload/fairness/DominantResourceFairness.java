package io.github.workload.fairness;

import io.github.workload.annotations.WIP;

/**
 * DRF，a generalization of max-min fairness to multiple resources.
 *
 * <p>简单讲：谁要的资源少，谁的优先级高。这样不会因为一个胖业务，饿死大批小业务</p>
 * <p>YARN/Mesos/K8S is using DRF.</p>
 *
 * @see <a href="https://static.usenix.org/event/nsdi11/tech/full_papers/Ghodsi.pdf">The DRF Paper</a>
 */
@WIP
class DominantResourceFairness {

}
