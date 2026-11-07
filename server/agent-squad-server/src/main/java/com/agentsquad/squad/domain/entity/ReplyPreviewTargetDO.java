package com.agentsquad.squad.domain.entity;

/**
 * 普通回复公开文本的所属信息
 * <p>
 * 从已受理会话读取稳定身份，仅自然输出且仍可执行的回复允许创建临时预览。
 */
public record ReplyPreviewTargetDO(String userId, String conversationId, String executionId, String sourceMessageId) {
}
