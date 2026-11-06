package com.agentsquad.squad.common.enums.conversation;

/**
 * 任务要求修改尚未应用的等待原因
 * <p>
 * 供后台决策与页面协议共用，等待不等于拒绝或已经应用。
 */
public enum TaskDirectiveWaitReason {
    NONE,
    WAITING_SAFE_POINT,
    WAITING_OPERATION,
    WAITING_USER,
    WAITING_COORDINATOR,
    COORDINATING
}
