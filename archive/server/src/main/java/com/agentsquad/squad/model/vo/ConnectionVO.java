package com.agentsquad.squad.model.vo;

import java.util.Map;

/**
 * 连接公开视图；永远只回 secretPresent，不回密钥本体或 secret_ref。
 */
@lombok.Getter
@lombok.EqualsAndHashCode
@lombok.experimental.Accessors(fluent = true)
public class ConnectionVO {

    @com.fasterxml.jackson.annotation.JsonProperty("connectionId")
    private final Long connectionId;

    @com.fasterxml.jackson.annotation.JsonProperty("name")
    private final String name;

    @com.fasterxml.jackson.annotation.JsonProperty("provider")
    private final String provider;

    @com.fasterxml.jackson.annotation.JsonProperty("environment")
    private final String environment;

    @com.fasterxml.jackson.annotation.JsonProperty("resourcePolicy")
    private final Map<String, Object> resourcePolicy;

    @com.fasterxml.jackson.annotation.JsonProperty("secretPresent")
    private final boolean secretPresent;

    @com.fasterxml.jackson.annotation.JsonProperty("revision")
    private final long revision;

    @com.fasterxml.jackson.annotation.JsonProperty("enabled")
    private final boolean enabled;

    @com.fasterxml.jackson.annotation.JsonCreator
    public ConnectionVO(
            @com.fasterxml.jackson.annotation.JsonProperty("connectionId") Long connectionId,
            @com.fasterxml.jackson.annotation.JsonProperty("name") String name,
            @com.fasterxml.jackson.annotation.JsonProperty("provider") String provider,
            @com.fasterxml.jackson.annotation.JsonProperty("environment") String environment,
            @com.fasterxml.jackson.annotation.JsonProperty("resourcePolicy") Map<String, Object> resourcePolicy,
            @com.fasterxml.jackson.annotation.JsonProperty("secretPresent") boolean secretPresent,
            @com.fasterxml.jackson.annotation.JsonProperty("revision") long revision,
            @com.fasterxml.jackson.annotation.JsonProperty("enabled") boolean enabled) {
        this.connectionId = connectionId;
        this.name = name;
        this.provider = provider;
        this.environment = environment;
        this.resourcePolicy = resourcePolicy;
        this.secretPresent = secretPresent;
        this.revision = revision;
        this.enabled = enabled;
    }
}
