package io.github.workload.overloading;

import io.github.workload.WorkloadPriority;
import io.github.workload.annotations.Experimental;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * 基于FQ-CoDel算法的工作负载准入控制器.
 *
 * <p>基本思路：if every request has experienced queueing delay greater than the target (5ms) during the past interval (100ms), then we shed load.</p>
 *
 * @see <a href="http://queue.acm.org/detail.cfm?id=2209336">Controlling Queue Delay</a>
 * @see <a href="http://queue.acm.org/detail.cfm?id=2839461">Fail at Scale Paper</a>
 * @see <a href="https://github.com/facebook/folly/blob/bd600cd4e88f664f285489c76b6ad835d8367cd2/folly/executors/Codel.h">Facebook adapted CoDel on folly</a>
 */
@Slf4j
@Experimental
class FqCodelAdmissionController implements AdmissionController {
    private final String name;

    FqCodelAdmissionController(String name) {
        this.name = name;
    }

    @Override
    public boolean admit(@NonNull WorkloadPriority priority) {
        return false;
    }

    @Override
    public void feedback(@NonNull WorkloadFeedback feedback) {

    }
}
