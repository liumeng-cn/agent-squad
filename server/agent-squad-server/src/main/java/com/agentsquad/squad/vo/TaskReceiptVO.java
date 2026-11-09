package com.agentsquad.squad.vo;

/**
 * 平台受理收据；QUEUED 表示数据库提交成功，不代表模型已启动。
 */
@lombok.Getter
@lombok.EqualsAndHashCode
@lombok.experimental.Accessors(fluent = true)
public class TaskReceiptVO {

    @com.fasterxml.jackson.annotation.JsonProperty("taskId")
    private final String taskId;

    @com.fasterxml.jackson.annotation.JsonProperty("commandId")
    private final String commandId;

    @com.fasterxml.jackson.annotation.JsonProperty("status")
    private final String status;

    @com.fasterxml.jackson.annotation.JsonCreator
    public TaskReceiptVO(
            @com.fasterxml.jackson.annotation.JsonProperty("taskId") String taskId,
            @com.fasterxml.jackson.annotation.JsonProperty("commandId") String commandId,
            @com.fasterxml.jackson.annotation.JsonProperty("status") String status) {
        this.taskId = taskId;
        this.commandId = commandId;
        this.status = status;
    }
}
