package com.agentsquad.squad.model.vo;

import java.util.List;

/**
 * 按真实活动时间排序的会话页。
 * <p>
 * 复合游标固定上一页末项的排序值，标题及日期均来自服务端事实。
 */
@lombok.Getter
@lombok.EqualsAndHashCode
@lombok.experimental.Accessors(fluent = true)
public class ConversationListPageVO {

    @com.fasterxml.jackson.annotation.JsonProperty("items")
    private final List<ConversationViewVO> items;

    @com.fasterxml.jackson.annotation.JsonProperty("nextCursor")
    private final String nextCursor;

    @com.fasterxml.jackson.annotation.JsonCreator
    public ConversationListPageVO(
            @com.fasterxml.jackson.annotation.JsonProperty("items") List<ConversationViewVO> items,
            @com.fasterxml.jackson.annotation.JsonProperty("nextCursor") String nextCursor) {
        this.items = items;
        this.nextCursor = nextCursor;
    }
}
