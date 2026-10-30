package com.agentsquad.squad.memory.dto;

/**
 * 记忆工具的稳定写入收据。
 * <p>
 * 仅保存身份与版本，不在幂等日志重复保存正文，删除后无法从回执重新注入旧值。
 */
public record MemoryWriteReceipt(String memoryId, long version, long memoryRevision, int affectedScopes,
                                 String impact) {
}
