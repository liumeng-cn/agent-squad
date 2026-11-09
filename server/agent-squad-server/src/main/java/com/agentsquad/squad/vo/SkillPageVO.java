package com.agentsquad.squad.vo;

import java.util.List;

/**
 * 个人技能版本分页结果。
 * <p>
 * 按存储顺序读取稳定分页，不混入其他用户的同名技能。
 */
@lombok.Getter
@lombok.EqualsAndHashCode
@lombok.experimental.Accessors(fluent = true)
public class SkillPageVO {

    @com.fasterxml.jackson.annotation.JsonProperty("items")
    private final List<SkillVersionVO> items;

    @com.fasterxml.jackson.annotation.JsonProperty("nextCursor")
    private final String nextCursor;

    @com.fasterxml.jackson.annotation.JsonCreator
    public SkillPageVO(
            @com.fasterxml.jackson.annotation.JsonProperty("items") List<SkillVersionVO> items,
            @com.fasterxml.jackson.annotation.JsonProperty("nextCursor") String nextCursor) {
        this.items = items;
        this.nextCursor = nextCursor;
    }
}
