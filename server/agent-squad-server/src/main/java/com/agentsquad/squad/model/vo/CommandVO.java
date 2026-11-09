package com.agentsquad.squad.model.vo;

/**
 * 归属受控的命令回执；原生状态只是提交瞬间快照，不代表运行终态。
 */
@lombok.Getter
@lombok.EqualsAndHashCode
@lombok.experimental.Accessors(fluent = true)
public class CommandVO {

    @com.fasterxml.jackson.annotation.JsonProperty("commandId")
    private final String commandId;

    @com.fasterxml.jackson.annotation.JsonProperty("taskId")
    private final String taskId;

    @com.fasterxml.jackson.annotation.JsonProperty("status")
    private final String status;

    @com.fasterxml.jackson.annotation.JsonProperty("nativeTurnId")
    private final String nativeTurnId;

    @com.fasterxml.jackson.annotation.JsonProperty("nativeRunId")
    private final String nativeRunId;

    @com.fasterxml.jackson.annotation.JsonProperty("nativeStatus")
    private final String nativeStatus;

    @com.fasterxml.jackson.annotation.JsonCreator
    public CommandVO(
            @com.fasterxml.jackson.annotation.JsonProperty("commandId") String commandId,
            @com.fasterxml.jackson.annotation.JsonProperty("taskId") String taskId,
            @com.fasterxml.jackson.annotation.JsonProperty("status") String status,
            @com.fasterxml.jackson.annotation.JsonProperty("nativeTurnId") String nativeTurnId,
            @com.fasterxml.jackson.annotation.JsonProperty("nativeRunId") String nativeRunId,
            @com.fasterxml.jackson.annotation.JsonProperty("nativeStatus") String nativeStatus) {
        this.commandId = commandId;
        this.taskId = taskId;
        this.status = status;
        this.nativeTurnId = nativeTurnId;
        this.nativeRunId = nativeRunId;
        this.nativeStatus = nativeStatus;
    }
}
