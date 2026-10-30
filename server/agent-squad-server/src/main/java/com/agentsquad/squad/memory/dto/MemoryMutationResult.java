package com.agentsquad.squad.memory.dto;

/**
 * 记忆变更收据。
 * <p>
 * 区分已提交记忆变更和仍被旧上下文阻塞的执行；不声称原生会话已清理。
 */
public record MemoryMutationResult(MemoryView memory, long memoryRevision, int affectedScopes, String impact) {
}
