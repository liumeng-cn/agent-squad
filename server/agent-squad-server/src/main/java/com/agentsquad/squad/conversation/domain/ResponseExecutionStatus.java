package com.agentsquad.squad.conversation.domain;

/**
 * 回复执行生命周期。
 * <p>
 * 普通回复与目标任务独立裁决；连接断开不属于执行状态。
 */
public enum ResponseExecutionStatus {
    QUEUED,
    RUNNING,
    WAITING_INPUT,
    WAITING_APPROVAL,
    RECOVERING,
    RECONCILING,
    CANCELLING,
    SUCCEEDED,
    PARTIAL,
    FAILED,
    CANCELLED
}
