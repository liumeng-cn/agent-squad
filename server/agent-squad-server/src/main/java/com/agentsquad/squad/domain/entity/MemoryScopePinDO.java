package com.agentsquad.squad.domain.entity;

/**
 * 执行记忆冻结点。
 * <p>
 * 同一Scope只冻结一次；纠正或删除后旧冻结点不能原位更新。
 */
public record MemoryScopePinDO(String scopeId, long memoryRevision, String barrierStatus) {
}
