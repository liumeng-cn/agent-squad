package com.agentsquad.squad.model.vo;


/**
 * 用户对技能版本的使用授权。
 * <p>
 * version 用于启停与撤销的并发检查，不改变技能正文。
 */
@lombok.Getter
@lombok.EqualsAndHashCode
@lombok.experimental.Accessors(fluent = true)
public class SkillGrantVO {

    @com.fasterxml.jackson.annotation.JsonProperty("skillVersionId")
    private final Long skillVersionId;

    @com.fasterxml.jackson.annotation.JsonProperty("enabled")
    private final boolean enabled;

    @com.fasterxml.jackson.annotation.JsonProperty("version")
    private final long version;

    @com.fasterxml.jackson.annotation.JsonCreator
    public SkillGrantVO(
            @com.fasterxml.jackson.annotation.JsonProperty("skillVersionId") Long skillVersionId,
            @com.fasterxml.jackson.annotation.JsonProperty("enabled") boolean enabled,
            @com.fasterxml.jackson.annotation.JsonProperty("version") long version) {
        this.skillVersionId = skillVersionId;
        this.enabled = enabled;
        this.version = version;
    }
}
