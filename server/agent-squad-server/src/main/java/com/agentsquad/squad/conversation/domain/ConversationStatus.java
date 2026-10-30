package com.agentsquad.squad.conversation.domain;

/**
 * 用户对话的生命周期状态。
 * <p>
 * ACTIVE 允许提交目标；DELETING 拦截新访问并等待在途目标收尾；DELETED 表示删除完成。
 */
public enum ConversationStatus {
    ACTIVE,
    DELETING,
    DELETED
}
