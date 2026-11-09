package com.agentsquad.squad.vo;


import java.util.List;

/**
 * Worker 能力定义分页视图。
 * <p>
 * 游标对应持久主键，供客户端连续读取已发布版本。
 */
@lombok.Getter
@lombok.EqualsAndHashCode
@lombok.experimental.Accessors(fluent = true)
public class WorkerDefinitionPageVO {

    @com.fasterxml.jackson.annotation.JsonProperty("items")
    private final List<WorkerDefinitionVO> items;

    @com.fasterxml.jackson.annotation.JsonProperty("nextCursor")
    private final String nextCursor;

    @com.fasterxml.jackson.annotation.JsonCreator
    public WorkerDefinitionPageVO(
            @com.fasterxml.jackson.annotation.JsonProperty("items") List<WorkerDefinitionVO> items,
            @com.fasterxml.jackson.annotation.JsonProperty("nextCursor") String nextCursor) {
        this.items = items;
        this.nextCursor = nextCursor;
    }
}
