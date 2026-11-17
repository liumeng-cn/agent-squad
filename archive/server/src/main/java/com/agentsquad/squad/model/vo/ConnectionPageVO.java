package com.agentsquad.squad.model.vo;


import java.util.List;

/**
 * 连接分页响应；nextCursor 为空表示没有更多。
 */
@lombok.Getter
@lombok.EqualsAndHashCode
@lombok.experimental.Accessors(fluent = true)
public class ConnectionPageVO {

    @com.fasterxml.jackson.annotation.JsonProperty("items")
    private final List<ConnectionVO> items;

    @com.fasterxml.jackson.annotation.JsonProperty("nextCursor")
    private final String nextCursor;

    @com.fasterxml.jackson.annotation.JsonCreator
    public ConnectionPageVO(
            @com.fasterxml.jackson.annotation.JsonProperty("items") List<ConnectionVO> items,
            @com.fasterxml.jackson.annotation.JsonProperty("nextCursor") String nextCursor) {
        this.items = items;
        this.nextCursor = nextCursor;
    }
}
