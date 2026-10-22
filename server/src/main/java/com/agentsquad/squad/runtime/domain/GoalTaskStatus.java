package com.agentsquad.squad.runtime.domain;

/**
 * 用户目标的产品状态。
 * <p>
 * 数据库存储枚举名称；运行控制只依据平台状态裁决，不能直接以原生回合状态替代。
 */
public enum GoalTaskStatus {
    QUEUED,
    RUNNING,
    WAITING_WORKERS,
    WAITING_INPUT,
    WAITING_APPROVAL,
    RECOVERY_BLOCKED,
    RECONCILING,
    CANCELLING,
    SUCCEEDED,
    PARTIAL,
    FAILED,
    CANCELLED;

    public boolean isTerminal() {
        return switch (this) {
            case SUCCEEDED, PARTIAL, FAILED, CANCELLED -> true;
            default -> false;
        };
    }

    public boolean isResumable() {
        return switch (this) {
            case WAITING_INPUT, WAITING_APPROVAL, RECOVERY_BLOCKED, RECONCILING -> true;
            default -> false;
        };
    }
}
