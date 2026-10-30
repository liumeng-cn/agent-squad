package com.agentsquad.squad.worker.domain;

/**
 * Worker 能力版本状态。
 * <p>
 * REVOKED 为终态，调度器只能选择 ENABLED 版本派发新任务。
 */
public enum WorkerDefinitionStatus {
    ENABLED,
    DISABLED,
    REVOKED;

    public boolean canChangeTo(WorkerDefinitionStatus next) {
        return this != REVOKED && next != null;
    }
}
