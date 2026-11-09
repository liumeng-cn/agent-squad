package com.agentsquad.squad.vo;


import java.util.List;

/**
 * 目标分页响应。
 */
@lombok.Getter
@lombok.EqualsAndHashCode
@lombok.experimental.Accessors(fluent = true)
public class GoalTaskPageVO {

    @com.fasterxml.jackson.annotation.JsonProperty("items")
    private final List<TaskVO> items;

    @com.fasterxml.jackson.annotation.JsonProperty("nextCursor")
    private final String nextCursor;

    @com.fasterxml.jackson.annotation.JsonCreator
    public GoalTaskPageVO(
            @com.fasterxml.jackson.annotation.JsonProperty("items") List<TaskVO> items,
            @com.fasterxml.jackson.annotation.JsonProperty("nextCursor") String nextCursor) {
        this.items = items;
        this.nextCursor = nextCursor;
    }
}
