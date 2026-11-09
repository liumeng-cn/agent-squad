package com.agentsquad.squad.model.dto;

/**
 * 消息活动历史查询
 * <p>
 * 使用上页最后一条活动的业务编号作为游标。
 */
@lombok.Getter
@lombok.EqualsAndHashCode
@lombok.experimental.Accessors(fluent = true)
public class ActivityHistoryDTO {

    @com.fasterxml.jackson.annotation.JsonProperty("sourceMessageId")
    private final Long sourceMessageId;

    @com.fasterxml.jackson.annotation.JsonProperty("beforeActivityId")
    private final Long beforeActivityId;

    @com.fasterxml.jackson.annotation.JsonCreator
    public ActivityHistoryDTO(
            @com.fasterxml.jackson.annotation.JsonProperty("sourceMessageId") Long sourceMessageId,
            @com.fasterxml.jackson.annotation.JsonProperty("beforeActivityId") Long beforeActivityId) {
        this.sourceMessageId = sourceMessageId;
        this.beforeActivityId = beforeActivityId;
    }
}
