package com.agentsquad.squad.model.vo;


/**
 * 用户记忆分页。
 * <p>
 * 以稳定存储游标分页，仅包含当前用户的显式记忆。
 */
@lombok.Getter
@lombok.EqualsAndHashCode
@lombok.experimental.Accessors(fluent = true)
public class MemoryPageVO {

    @com.fasterxml.jackson.annotation.JsonProperty("items")
    private final java.util.List<MemoryVO> items;

    @com.fasterxml.jackson.annotation.JsonProperty("nextCursor")
    private final String nextCursor;

    @com.fasterxml.jackson.annotation.JsonProperty("memoryRevision")
    private final long memoryRevision;

    @com.fasterxml.jackson.annotation.JsonProperty("affectedScopes")
    private final int affectedScopes;

    @com.fasterxml.jackson.annotation.JsonCreator
    public MemoryPageVO(
            @com.fasterxml.jackson.annotation.JsonProperty("items") java.util.List<MemoryVO> items,
            @com.fasterxml.jackson.annotation.JsonProperty("nextCursor") String nextCursor,
            @com.fasterxml.jackson.annotation.JsonProperty("memoryRevision") long memoryRevision,
            @com.fasterxml.jackson.annotation.JsonProperty("affectedScopes") int affectedScopes) {
        this.items = items;
        this.nextCursor = nextCursor;
        this.memoryRevision = memoryRevision;
        this.affectedScopes = affectedScopes;
    }
}
