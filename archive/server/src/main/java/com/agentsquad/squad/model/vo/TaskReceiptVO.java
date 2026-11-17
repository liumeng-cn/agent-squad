package com.agentsquad.squad.model.vo;

/**
 * 平台受理收据；QUEUED 表示数据库提交成功，不代表模型已启动。
 */
@lombok.Getter
@lombok.EqualsAndHashCode
@lombok.experimental.Accessors(fluent = true)
public class TaskReceiptVO {

    @com.fasterxml.jackson.annotation.JsonProperty("taskId")
    private final Long taskId;

    @com.fasterxml.jackson.annotation.JsonProperty("commandId")
    private final Long commandId;

    @com.fasterxml.jackson.annotation.JsonProperty("status")
    private final String status;

    @com.fasterxml.jackson.annotation.JsonCreator
    public TaskReceiptVO(
            @com.fasterxml.jackson.annotation.JsonProperty("taskId") Long taskId,
            @com.fasterxml.jackson.annotation.JsonProperty("commandId") Long commandId,
            @com.fasterxml.jackson.annotation.JsonProperty("status") String status) {
        this.taskId = taskId;
        this.commandId = commandId;
        this.status = status;
    }
}
