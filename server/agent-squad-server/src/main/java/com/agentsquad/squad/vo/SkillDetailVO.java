package com.agentsquad.squad.vo;


import java.util.List;

/**
 * 个人技能版本详情。
 * <p>
 * 正文仅对所属用户可见；授权单独返回以支持页面乐观更新。
 */
@lombok.Getter
@lombok.EqualsAndHashCode
@lombok.experimental.Accessors(fluent = true)
public class SkillDetailVO {

    @com.fasterxml.jackson.annotation.JsonProperty("skill")
    private final SkillVersionVO skill;

    @com.fasterxml.jackson.annotation.JsonProperty("files")
    private final List<SkillFileVO> files;

    @com.fasterxml.jackson.annotation.JsonProperty("grant")
    private final SkillGrantVO grant;

    @com.fasterxml.jackson.annotation.JsonCreator
    public SkillDetailVO(
            @com.fasterxml.jackson.annotation.JsonProperty("skill") SkillVersionVO skill,
            @com.fasterxml.jackson.annotation.JsonProperty("files") List<SkillFileVO> files,
            @com.fasterxml.jackson.annotation.JsonProperty("grant") SkillGrantVO grant) {
        this.skill = skill;
        this.files = files;
        this.grant = grant;
    }
}
