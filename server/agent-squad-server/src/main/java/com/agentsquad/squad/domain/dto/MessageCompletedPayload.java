package com.agentsquad.squad.domain.dto;

/**
 * 正式消息完成增量
 * <p>
 * 携带完整公开消息及修订，支持新消息插入与已有草稿替换，不携带模型内部上下文。
 */
public record MessageCompletedPayload(int schemaVersion, MessageView message) {
}
