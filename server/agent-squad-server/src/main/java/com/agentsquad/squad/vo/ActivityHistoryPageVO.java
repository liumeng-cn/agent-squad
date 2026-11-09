package com.agentsquad.squad.vo;


import java.util.List;

/**
 * 消息活动历史页
 * <p>
 * 按活动创建顺序倒序返回，游标为空表示已到末页。
 */
@lombok.Getter
@lombok.EqualsAndHashCode
@lombok.experimental.Accessors(fluent = true)
public class ActivityHistoryPageVO {

    @com.fasterxml.jackson.annotation.JsonProperty("items")
    private final List<ExecutionActivityVO> items;

    @com.fasterxml.jackson.annotation.JsonProperty("nextCursor")
    private final String nextCursor;

    @com.fasterxml.jackson.annotation.JsonCreator
    public ActivityHistoryPageVO(
            @com.fasterxml.jackson.annotation.JsonProperty("items") List<ExecutionActivityVO> items,
            @com.fasterxml.jackson.annotation.JsonProperty("nextCursor") String nextCursor) {
        this.items = items;
        this.nextCursor = nextCursor;
    }
}
