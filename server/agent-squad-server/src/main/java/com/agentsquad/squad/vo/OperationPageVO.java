package com.agentsquad.squad.vo;

import java.util.List;

/**
 * 目标操作账本分页视图；nextCursor 为最后一条的存储游标，null 表示没有更多。
 */
@lombok.Getter
@lombok.EqualsAndHashCode
@lombok.experimental.Accessors(fluent = true)
public class OperationPageVO {

    @com.fasterxml.jackson.annotation.JsonProperty("items")
    private final List<OperationVO> items;

    @com.fasterxml.jackson.annotation.JsonProperty("nextCursor")
    private final String nextCursor;

    @com.fasterxml.jackson.annotation.JsonCreator
    public OperationPageVO(
            @com.fasterxml.jackson.annotation.JsonProperty("items") List<OperationVO> items,
            @com.fasterxml.jackson.annotation.JsonProperty("nextCursor") String nextCursor) {
        this.items = items;
        this.nextCursor = nextCursor;
    }
}
