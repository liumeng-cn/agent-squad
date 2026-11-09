package com.agentsquad.squad.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * 创建连接请求；secret 只写不读，服务端加密入保险库。
 */
@lombok.Getter
@lombok.EqualsAndHashCode
@lombok.experimental.Accessors(fluent = true)
public class ConnectionCreateDTO {

    @com.fasterxml.jackson.annotation.JsonProperty("name")
    @NotBlank
    @Size(max = 100)
    private final String name;

    @com.fasterxml.jackson.annotation.JsonProperty("provider")
    @NotBlank
    @Size(max = 80)
    private final String provider;

    @com.fasterxml.jackson.annotation.JsonProperty("environment")
    @NotBlank
    @Size(max = 80)
    private final String environment;

    @com.fasterxml.jackson.annotation.JsonProperty("resourcePolicy")
    @NotNull
    private final Map<String, Object> resourcePolicy;

    @com.fasterxml.jackson.annotation.JsonProperty("secret")
    @NotBlank
    @Size(max = 16384)
    private final String secret;

    @com.fasterxml.jackson.annotation.JsonCreator
    public ConnectionCreateDTO(
            @com.fasterxml.jackson.annotation.JsonProperty("name") String name,
            @com.fasterxml.jackson.annotation.JsonProperty("provider") String provider,
            @com.fasterxml.jackson.annotation.JsonProperty("environment") String environment,
            @com.fasterxml.jackson.annotation.JsonProperty("resourcePolicy") Map<String, Object> resourcePolicy,
            @com.fasterxml.jackson.annotation.JsonProperty("secret") String secret) {
        this.name = name;
        this.provider = provider;
        this.environment = environment;
        this.resourcePolicy = resourcePolicy;
        this.secret = secret;
    }
}
