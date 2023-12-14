package io.github.workload.sentinel;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.EntryType;
import com.alibaba.csp.sentinel.SphO;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.node.Node;
import com.alibaba.csp.sentinel.slotchain.ResourceWrapper;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.authority.AuthorityException;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeException;
import com.alibaba.csp.sentinel.slots.block.flow.FlowException;
import com.alibaba.csp.sentinel.slots.block.flow.controller.WarmUpController;
import com.alibaba.csp.sentinel.slots.system.SystemBlockException;
import com.alibaba.csp.sentinel.slots.system.SystemRuleManager;

/**
 * rt, qps, thread count, cpu usage, cpu load avg.
 * <p>
 * {@link SystemRuleManager#checkSystem(ResourceWrapper, int)}
 * {@link WarmUpController#canPass(Node, int, boolean)}
 * {@link RuleConstant#CONTROL_BEHAVIOR_WARM_UP_RATE_LIMITER}
 */
class SentinelDemo {
    private String resourceName = "foo";

    void useWithIfElse() {
        if (SphO.entry(resourceName)) {
            try {
                // execute my logic
            } finally {
                SphO.exit();
            }
        }
    }

    void useWithTryCatch() {
        try (Entry entry = SphU.entry(resourceName)) {
            // execute my logic
        } catch (BlockException e) {
            if (e instanceof SystemBlockException) {

            }
            if (e instanceof DegradeException) {

            }
            if (e instanceof AuthorityException) {

            }
            if (e instanceof FlowException) {

            }
        } finally {

        }
    }

    @SentinelResource(entryType = EntryType.IN)
    public interface MyApi {
        Integer ageOfUser(Long uid);
    }
}
