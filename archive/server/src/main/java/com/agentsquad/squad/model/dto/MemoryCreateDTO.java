package com.agentsquad.squad.model.dto;

/**
 * 用户显式创建记忆请求。
 * <p>
 * 设置页的确认操作即写入授权；身份取登录态而非请求字段。
 * 同用户有效记忆键唯一；旧记录删除后可以同键创建新记忆身份，旧删除记录保持不变。
 */
@lombok.Getter
@lombok.EqualsAndHashCode
@lombok.experimental.Accessors(fluent = true)
public class MemoryCreateDTO {

    @com.fasterxml.jackson.annotation.JsonProperty("key")
    @jakarta.validation.constraints.NotBlank
    @jakarta.validation.constraints.Size(max = 100)
    private final String key;

    @com.fasterxml.jackson.annotation.JsonProperty("content")
    @jakarta.validation.constraints.NotBlank
    @jakarta.validation.constraints.Size(max = 8000)
    private final String content;

    @com.fasterxml.jackson.annotation.JsonProperty("expiresAt")
    private final java.time.OffsetDateTime expiresAt;

    @com.fasterxml.jackson.annotation.JsonCreator
    public MemoryCreateDTO(
            @com.fasterxml.jackson.annotation.JsonProperty("key") String key,
            @com.fasterxml.jackson.annotation.JsonProperty("content") String content,
            @com.fasterxml.jackson.annotation.JsonProperty("expiresAt") java.time.OffsetDateTime expiresAt) {
        this.key = key;
        this.content = content;
        this.expiresAt = expiresAt;
    }
}
