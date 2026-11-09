package com.agentsquad.squad.dto;

/**
 * 用户纠正记忆请求。
 * <p>
 * 必须携带读取时的版本，冲突不得覆盖新值。
 */
@lombok.Getter
@lombok.EqualsAndHashCode
@lombok.experimental.Accessors(fluent = true)
public class MemoryUpdateDTO {

    @com.fasterxml.jackson.annotation.JsonProperty(value = "expectedVersion", required = true)
    @jakarta.validation.constraints.Min(0)
    private final long expectedVersion;

    @com.fasterxml.jackson.annotation.JsonProperty("content")
    @jakarta.validation.constraints.NotBlank
    @jakarta.validation.constraints.Size(max = 8000)
    private final String content;

    @com.fasterxml.jackson.annotation.JsonProperty("expiresAt")
    private final java.time.OffsetDateTime expiresAt;

    @com.fasterxml.jackson.annotation.JsonCreator
    public MemoryUpdateDTO(
            @com.fasterxml.jackson.annotation.JsonProperty(value = "expectedVersion", required = true) long expectedVersion,
            @com.fasterxml.jackson.annotation.JsonProperty("content") String content,
            @com.fasterxml.jackson.annotation.JsonProperty("expiresAt") java.time.OffsetDateTime expiresAt) {
        this.expectedVersion = expectedVersion;
        this.content = content;
        this.expiresAt = expiresAt;
    }
}
