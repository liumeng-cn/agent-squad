package com.agentsquad.squad.model.vo;

/**
 * 用户可见的记忆。
 * <p>
 * 正文仅面向所有者；删除后返回空内容，墓碑与原始来源引用保留在服务端。
 */
@lombok.Getter
@lombok.EqualsAndHashCode
@lombok.experimental.Accessors(fluent = true)
public class MemoryVO {

    @com.fasterxml.jackson.annotation.JsonProperty("memoryId")
    private final Long memoryId;

    @com.fasterxml.jackson.annotation.JsonProperty("key")
    private final String key;

    @com.fasterxml.jackson.annotation.JsonProperty("content")
    private final String content;

    @com.fasterxml.jackson.annotation.JsonProperty("kind")
    private final String kind;

    @com.fasterxml.jackson.annotation.JsonProperty("status")
    private final String status;

    @com.fasterxml.jackson.annotation.JsonProperty("version")
    private final long version;

    @com.fasterxml.jackson.annotation.JsonProperty("expiresAt")
    private final java.time.OffsetDateTime expiresAt;

    @com.fasterxml.jackson.annotation.JsonProperty("sourceMessageId")
    private final Long sourceMessageId;

    @com.fasterxml.jackson.annotation.JsonCreator
    public MemoryVO(
            @com.fasterxml.jackson.annotation.JsonProperty("memoryId") Long memoryId,
            @com.fasterxml.jackson.annotation.JsonProperty("key") String key,
            @com.fasterxml.jackson.annotation.JsonProperty("content") String content,
            @com.fasterxml.jackson.annotation.JsonProperty("kind") String kind,
            @com.fasterxml.jackson.annotation.JsonProperty("status") String status,
            @com.fasterxml.jackson.annotation.JsonProperty("version") long version,
            @com.fasterxml.jackson.annotation.JsonProperty("expiresAt") java.time.OffsetDateTime expiresAt,
            @com.fasterxml.jackson.annotation.JsonProperty("sourceMessageId") Long sourceMessageId) {
        this.memoryId = memoryId;
        this.key = key;
        this.content = content;
        this.kind = kind;
        this.status = status;
        this.version = version;
        this.expiresAt = expiresAt;
        this.sourceMessageId = sourceMessageId;
    }
}
