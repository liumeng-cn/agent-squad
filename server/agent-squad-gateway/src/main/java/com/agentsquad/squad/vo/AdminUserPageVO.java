package com.agentsquad.squad.vo;

import java.util.List;

/**
 * 用户摘要分页结果；游标为上一页最后一个用户业务 ID。
 */
@lombok.Getter
@lombok.EqualsAndHashCode
@lombok.experimental.Accessors(fluent = true)
public class AdminUserPageVO {

    @com.fasterxml.jackson.annotation.JsonProperty("items")
    private final List<AdminUserVO> items;

    @com.fasterxml.jackson.annotation.JsonProperty("nextCursor")
    private final String nextCursor;

    @com.fasterxml.jackson.annotation.JsonCreator
    public AdminUserPageVO(
            @com.fasterxml.jackson.annotation.JsonProperty("items") List<AdminUserVO> items,
            @com.fasterxml.jackson.annotation.JsonProperty("nextCursor") String nextCursor) {
        this.items = items;
        this.nextCursor = nextCursor;
    }
}
