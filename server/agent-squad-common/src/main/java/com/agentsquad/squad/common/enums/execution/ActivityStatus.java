package com.agentsquad.squad.common.enums.execution;

/**
 * 执行活动状态
 * <p>
 * 用于持久活动与页面契约，不替代回复、目标或Worker的业务完成状态。
 */
public enum ActivityStatus {
    QUEUED,
    RUNNING,
    WAITING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    UNKNOWN
}
