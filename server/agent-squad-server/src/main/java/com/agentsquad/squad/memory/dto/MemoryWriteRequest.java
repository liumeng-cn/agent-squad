package com.agentsquad.squad.memory.dto;

/**
 * 显式记忆工具命令。
 * <p>
 * 模型只表达动作与匹配的用户原话，实际来源由服务端会话绑定推导。
 */
public record MemoryWriteRequest(String logicalKey, String action, String key, String content, Long expectedVersion,
                                 java.time.OffsetDateTime expiresAt, String sourceQuote) {
}
