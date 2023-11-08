package io.github.workload.overloading;

import java.io.Serializable;

/**
 * 基于优先级的准入等级游标，可以理解为水位线.
 *
 * <p>通过移动该游标，控制抛弃哪些请求.</p>
 * <p>过载严重，则准入等级更严格，抛弃更多请求；过载降低，则准入等级变宽松，接收更多请求.</p>
 * <p>每个进程维护自己的准入等级：LocalAdmissionLevel，同时上游维护所有下游的AdmissionLevel，下游通过piggyback机制把自己的LocalAdmissionLevel传递给上游</p>
 * <p>这样形成背压机制，上游请求下游时(子请求)会判断下游当前准入等级：最小化不必要的资源浪费</p>
 */
class AdmissionLevel implements Serializable {
    private static final long serialVersionUID = 6373611532663483048L;

    private WorkloadPriority priority;

    static AdmissionLevel ofAdmitAll() {
        return new AdmissionLevel(WorkloadPriority.ofLowestPriority());
    }

    private AdmissionLevel(WorkloadPriority priority) {
        this.priority = priority;
    }

    void changeTo(WorkloadPriority priority) {
        this.priority = priority;
    }

    int P() {
        return priority.P();
    }

    boolean admit(WorkloadPriority workloadPriority) {
        return workloadPriority.P() <= this.priority.P();
    }

}
