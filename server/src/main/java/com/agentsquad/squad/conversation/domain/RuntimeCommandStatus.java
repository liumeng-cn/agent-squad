package com.agentsquad.squad.conversation.domain;

/**
 * 平台持久命令的投递状态。
 * <p>
 * 状态反映平台和原生会话之间的受理进度，不代表用户目标已完成。
 */
public enum RuntimeCommandStatus {
    PENDING,
    DELIVERING,
    DELIVERED,
    APPLIED,
    BLOCKED,
    CANCELLED
}
