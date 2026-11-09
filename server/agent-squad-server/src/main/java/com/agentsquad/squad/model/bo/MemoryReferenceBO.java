package com.agentsquad.squad.model.bo;

/**
 * 执行使用的记忆引用。
 * <p>
 * 固定业务ID、内容版本与有效期，注入前必须回权威记录复验。
 */
public record MemoryReferenceBO(String memoryId, String key, String content, long version,
                              java.time.LocalDateTime expiresAt) {
}
