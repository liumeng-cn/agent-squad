package com.agentsquad.squad.model.vo;

/**
 * 控制命令受理收据；状态为命令在平台命令表中的状态，不代表模型已执行。
 */
@lombok.Getter
@lombok.EqualsAndHashCode
@lombok.experimental.Accessors(fluent = true)
public class CommandReceiptVO {

    @com.fasterxml.jackson.annotation.JsonProperty("commandId")
    private final Long commandId;

    @com.fasterxml.jackson.annotation.JsonProperty("taskId")
    private final Long taskId;

    @com.fasterxml.jackson.annotation.JsonProperty("status")
    private final String status;

    @com.fasterxml.jackson.annotation.JsonProperty("targetFrameworkTurnId")
    private final String targetFrameworkTurnId;

    @com.fasterxml.jackson.annotation.JsonProperty("reason")
    private final String reason;

    @com.fasterxml.jackson.annotation.JsonCreator
    public CommandReceiptVO(
            @com.fasterxml.jackson.annotation.JsonProperty("commandId") Long commandId,
            @com.fasterxml.jackson.annotation.JsonProperty("taskId") Long taskId,
            @com.fasterxml.jackson.annotation.JsonProperty("status") String status,
            @com.fasterxml.jackson.annotation.JsonProperty("targetFrameworkTurnId") String targetFrameworkTurnId,
            @com.fasterxml.jackson.annotation.JsonProperty("reason") String reason) {
        this.commandId = commandId;
        this.taskId = taskId;
        this.status = status;
        this.targetFrameworkTurnId = targetFrameworkTurnId;
        this.reason = reason;
    }
}
