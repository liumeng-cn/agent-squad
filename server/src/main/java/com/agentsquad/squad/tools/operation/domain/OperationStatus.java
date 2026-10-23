package com.agentsquad.squad.tools.operation.domain;

/**
 * 外部操作生命周期；UNKNOWN 只能经核对出账，禁止跳到成功终态。
 */
public enum OperationStatus {
    PREPARED,
    WAITING_APPROVAL,
    IN_FLIGHT,
    UNKNOWN,
    RECONCILING,
    SUCCEEDED,
    FAILED,
    CANCELLED;

    /**
     * 是否允许新的审批或派发动作推进该状态。
     */
    public boolean isAdvanceable() {
        return this == PREPARED || this == WAITING_APPROVAL;
    }
}
