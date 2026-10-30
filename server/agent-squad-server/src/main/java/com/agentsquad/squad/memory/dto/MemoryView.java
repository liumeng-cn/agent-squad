package com.agentsquad.squad.memory.dto;

/**
 * 用户可见的记忆。
 * <p>
 * 正文仅面向所有者；删除后返回空内容，墓碑与原始来源引用保留在服务端。
 */
public record MemoryView(String memoryId, String key, String content, String kind, String status, long version,
                         java.time.OffsetDateTime expiresAt, String sourceMessageId) {
}
