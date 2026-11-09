package com.agentsquad.squad.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * 更新连接请求；仅允许启停、资源策略与密钥轮换，expectedVersion 必填做乐观并发控制。
 */
@lombok.Getter
@lombok.EqualsAndHashCode
@lombok.experimental.Accessors(fluent = true)
public class ConnectionUpdateDTO {

    @com.fasterxml.jackson.annotation.JsonProperty("expectedVersion")
    @NotNull
    private final Long expectedVersion;

    @com.fasterxml.jackson.annotation.JsonProperty("enabled")
    private final Boolean enabled;

    @com.fasterxml.jackson.annotation.JsonProperty("resourcePolicy")
    @Size(max = 16384)
    private final Map<String, Object> resourcePolicy;

    @com.fasterxml.jackson.annotation.JsonProperty("secret")
    @Size(max = 16384)
    private final String secret;

    @com.fasterxml.jackson.annotation.JsonCreator
    public ConnectionUpdateDTO(
            @com.fasterxml.jackson.annotation.JsonProperty("expectedVersion") Long expectedVersion,
            @com.fasterxml.jackson.annotation.JsonProperty("enabled") Boolean enabled,
            @com.fasterxml.jackson.annotation.JsonProperty("resourcePolicy") Map<String, Object> resourcePolicy,
            @com.fasterxml.jackson.annotation.JsonProperty("secret") String secret) {
        this.expectedVersion = expectedVersion;
        this.enabled = enabled;
        this.resourcePolicy = resourcePolicy;
        this.secret = secret;
    }
}
