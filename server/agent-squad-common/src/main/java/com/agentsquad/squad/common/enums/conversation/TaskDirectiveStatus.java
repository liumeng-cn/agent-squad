package com.agentsquad.squad.common.enums.conversation;

/**
 * 任务要求修改的处理状态
 * <p>
 * 供后台决策与页面协议共用，等待不等于拒绝或已经应用。
 */
public enum TaskDirectiveStatus {
    ACCEPTED,
    APPLIED,
    REJECTED
}
