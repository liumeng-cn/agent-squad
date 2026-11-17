package com.agentsquad.squad.model.vo;


/**
 * 记忆变更收据。
 * <p>
 * 区分已提交记忆变更和仍被旧上下文阻塞的执行；不声称原生会话已清理。
 */
@lombok.Getter
@lombok.EqualsAndHashCode
@lombok.experimental.Accessors(fluent = true)
public class MemoryMutationVO {

    @com.fasterxml.jackson.annotation.JsonProperty("memory")
    private final MemoryVO memory;

    @com.fasterxml.jackson.annotation.JsonProperty("memoryRevision")
    private final long memoryRevision;

    @com.fasterxml.jackson.annotation.JsonProperty("affectedScopes")
    private final int affectedScopes;

    @com.fasterxml.jackson.annotation.JsonProperty("impact")
    private final String impact;

    @com.fasterxml.jackson.annotation.JsonCreator
    public MemoryMutationVO(
            @com.fasterxml.jackson.annotation.JsonProperty("memory") MemoryVO memory,
            @com.fasterxml.jackson.annotation.JsonProperty("memoryRevision") long memoryRevision,
            @com.fasterxml.jackson.annotation.JsonProperty("affectedScopes") int affectedScopes,
            @com.fasterxml.jackson.annotation.JsonProperty("impact") String impact) {
        this.memory = memory;
        this.memoryRevision = memoryRevision;
        this.affectedScopes = affectedScopes;
        this.impact = impact;
    }
}
