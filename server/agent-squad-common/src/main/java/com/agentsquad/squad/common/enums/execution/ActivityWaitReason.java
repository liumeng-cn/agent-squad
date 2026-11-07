package com.agentsquad.squad.common.enums.execution;

/**
 * 执行活动等待原因
 * <p>
 * 用于持久活动与页面契约，不替代回复、目标或Worker的业务完成状态。
 */
public enum ActivityWaitReason {
    CAPACITY,
    DEPENDENCY,
    INPUT,
    APPROVAL,
    EXTERNAL_RESULT,
    RESUME_READY,
    RETRY_BACKOFF,
    EVENT_RECOVERY
}
