package com.agentsquad.squad.common.enums.worker;

/**
 * 委派工作状态
 * <p>
 * 区分依赖等待、实际执行、等待恢复及明确结束；等待恢复不能当作依赖已经彻底失败。
 */
public enum WorkerJobStatus {
    BLOCKED, READY, RUNNING, WAITING_INPUT, WAITING_APPROVAL, RECOVERING,
    RECOVERY_BLOCKED, RECONCILING, CANCELLING, SUCCEEDED, PARTIAL, FAILED,
    CANCELLED, SKIPPED, SUPERSEDED;

    public boolean preventsDependentExecution() {
        return this == FAILED || this == PARTIAL || this == CANCELLED || this == SKIPPED;
    }
}
