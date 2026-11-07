package com.agentsquad.squad.domain.entity;

/**
 * 记忆存储记录。
 * <p>
 * 保存用户显式内容、来源、乐观版本与删除墓碑；不直接作为接口响应。
 */
public record MemoryDO(String memoryId, String userId, String key, String content, String status, long version,
                        java.time.LocalDateTime expiresAt, String sourceMessageId, String sourceQuote, long storageId) {
}
