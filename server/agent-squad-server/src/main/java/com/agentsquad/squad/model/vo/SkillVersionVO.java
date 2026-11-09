package com.agentsquad.squad.model.vo;


/**
 * 技能不可变版本的公开信息。
 * <p>
 * 内容版本与用户启用状态分开维护，摘要可用于运行证据核对。
 */
@lombok.Getter
@lombok.EqualsAndHashCode
@lombok.experimental.Accessors(fluent = true)
public class SkillVersionVO {

    @com.fasterxml.jackson.annotation.JsonProperty("skillVersionId")
    private final String skillVersionId;

    @com.fasterxml.jackson.annotation.JsonProperty("name")
    private final String name;

    @com.fasterxml.jackson.annotation.JsonProperty("version")
    private final String version;

    @com.fasterxml.jackson.annotation.JsonProperty("contentHash")
    private final String contentHash;

    @com.fasterxml.jackson.annotation.JsonProperty("description")
    private final String description;

    @com.fasterxml.jackson.annotation.JsonProperty("status")
    private final String status;

    @com.fasterxml.jackson.annotation.JsonCreator
    public SkillVersionVO(
            @com.fasterxml.jackson.annotation.JsonProperty("skillVersionId") String skillVersionId,
            @com.fasterxml.jackson.annotation.JsonProperty("name") String name,
            @com.fasterxml.jackson.annotation.JsonProperty("version") String version,
            @com.fasterxml.jackson.annotation.JsonProperty("contentHash") String contentHash,
            @com.fasterxml.jackson.annotation.JsonProperty("description") String description,
            @com.fasterxml.jackson.annotation.JsonProperty("status") String status) {
        this.skillVersionId = skillVersionId;
        this.name = name;
        this.version = version;
        this.contentHash = contentHash;
        this.description = description;
        this.status = status;
    }
}
