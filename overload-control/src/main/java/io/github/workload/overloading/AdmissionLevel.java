package io.github.workload.overloading;

import lombok.extern.slf4j.Slf4j;

/**
 * 准入等级水位线.
 *
 * <p>通过移动该水位，控制抛弃哪些请求.</p>
 * <pre>
 *     WorkloadPriority.P
 *             ┌──┐
 *     ∧       │  │ reject
 *     │       │──┘
 * breakwater ─│──┐
 *     │       │  │
 *     ∨       │  │
 *             │  │ admit
 *             │  │
 *           0 └──┘
 * </pre>
 * <p>过载严重，则准入等级更严格，抛弃更多请求；过载降低，则准入等级变宽松，接收更多请求.</p>
 * <p>每个进程维护自己的准入等级：LocalAdmissionLevel，同时上游维护所有下游的AdmissionLevel，下游通过piggyback机制把自己的LocalAdmissionLevel传递给上游</p>
 * <p>这样形成背压机制，上游请求下游时(子请求)会判断下游当前准入等级：最小化不必要的资源浪费</p>
 *
 * <pre>
 * │<────────────────────── high priority ──────────────────────────────
 * │<───── B=0 ─────>│<──────────────── B=3 ────────────────>│<─  B=8 ─>
 * +─────────────────+───────────────────────────────────────+──────────
 * │ 0 │ 5 │ 8 │ 127 │ 1 │ 2 │ 7 │ 12 │ 50 │ 101 │ 102 │ 115 │ ......
 * +─────────────────+───────────────────────────────────────+──────────
 *   │   │                              │
 *   U   U                              │
 *                              AdmissionLevel
 * AdmissionLevel游标=(3, 50)，意味着，所有B>3的请求被抛弃，所有(B=3, U>50)的请求被抛弃
 * 移动该游标，向左意味着负载加剧，向右意味着负载减轻
 * </pre>
 */
@Slf4j
class AdmissionLevel {
    // 防波堤
    private WorkloadPriority breakwater;

    static AdmissionLevel ofAdmitAll() {
        AdmissionLevel level = new AdmissionLevel();
        // 只防住最低优先级，意味着全放行
        level.breakwater = WorkloadPriority.ofLowestPriority();
        return level;
    }

    void changeTo(WorkloadPriority priority) {
        int delta = priority.P() - this.P();
        if (delta == 0) {
            return;
        }

        log.warn("changed to admit {}: {} -> {}", delta > 0 ? "more" : "less", this.breakwater, priority);
        this.breakwater = priority;
    }

    int P() {
        return breakwater.P();
    }

    boolean admit(WorkloadPriority workloadPriority) {
        return workloadPriority.P() <= this.breakwater.P();
    }

}
