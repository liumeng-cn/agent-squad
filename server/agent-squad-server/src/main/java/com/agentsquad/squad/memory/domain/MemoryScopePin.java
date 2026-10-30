package com.agentsquad.squad.memory.domain;

/**
 * 执行记忆冻结点。
 * <p>
 * 同一Scope只冻结一次；纠正或删除后旧冻结点不能原位更新。
 */
public record MemoryScopePin(String scopeId, long memoryRevision, String barrierStatus) {
}
