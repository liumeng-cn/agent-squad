package com.agentsquad.squad.vo;

import java.util.List;

/**
 * 对话分页响应。
 */
@lombok.Getter
@lombok.EqualsAndHashCode
@lombok.experimental.Accessors(fluent = true)
public class ConversationPageVO {

    @com.fasterxml.jackson.annotation.JsonProperty("items")
    private final List<ConversationVO> items;

    @com.fasterxml.jackson.annotation.JsonProperty("nextCursor")
    private final String nextCursor;

    @com.fasterxml.jackson.annotation.JsonCreator
    public ConversationPageVO(
            @com.fasterxml.jackson.annotation.JsonProperty("items") List<ConversationVO> items,
            @com.fasterxml.jackson.annotation.JsonProperty("nextCursor") String nextCursor) {
        this.items = items;
        this.nextCursor = nextCursor;
    }
}
