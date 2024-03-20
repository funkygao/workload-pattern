package io.github.workload.overloading;

import io.github.workload.WorkloadPriority;
import io.github.workload.annotations.Immutable;
import io.github.workload.annotations.VisibleForTesting;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;

/**
 * 准入等级水位线.
 *
 * <p>通过移动该水位，控制抛弃哪些请求.</p>
 * <pre>
 *     WorkloadPriority.P
 *             ┌──┐
 *     ∧       │  │ reject
 *     │       │──┘
 *    bar ─────│──┐
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
@Immutable
class AdmissionLevel {
    private static final AdmissionLevel ADMIT_ALL = new AdmissionLevel(WorkloadPriority.ofLowest());

    /**
     * 准入门槛，在一个窗口期内不变.
     *
     * <p>优先级低于门槛值的请求都应该拒绝.</p>
     */
    @Getter(AccessLevel.PACKAGE)
    private final WorkloadPriority bar;

    @VisibleForTesting
    AdmissionLevel(@NonNull WorkloadPriority bar) {
        this.bar = bar;
    }

    static AdmissionLevel ofAdmitAll() {
        return ADMIT_ALL;
    }

    AdmissionLevel changeBar(int targetP) {
        if (targetP == this.P()) {
            return this;
        }

        return new AdmissionLevel(WorkloadPriority.fromP(targetP));
    }

    boolean admit(WorkloadPriority priority) {
        return priority.P() <= this.P();
    }

    /**
     * The normalized bar priority value.
     */
    int P() {
        return bar.P();
    }

    @Override
    public String toString() {
        return "AdmissionLevel(B=" + bar.B() + ",U=" + bar.U() + ";P=" + bar.P() + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }

        if (!(o instanceof AdmissionLevel)) {
            return false;
        }

        AdmissionLevel that = (AdmissionLevel) o;
        return that.bar.equals(this.bar);
    }

    @Override
    public int hashCode() {
        return bar.P();
    }

}
