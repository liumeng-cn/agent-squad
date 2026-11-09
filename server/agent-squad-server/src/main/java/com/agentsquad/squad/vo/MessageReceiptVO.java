package com.agentsquad.squad.vo;

import com.agentsquad.squad.enums.ResponseExecutionStatus;

/**
 * 消息持久受理收据。
 * <p>
 * 202 只证明持久接受；不证明模型启动，也不意味着已经创建目标任务。
 */
@lombok.Getter
@lombok.EqualsAndHashCode
@lombok.experimental.Accessors(fluent = true)
public class MessageReceiptVO {

    @com.fasterxml.jackson.annotation.JsonProperty("conversationId")
    private final String conversationId;

    @com.fasterxml.jackson.annotation.JsonProperty("messageId")
    private final String messageId;

    @com.fasterxml.jackson.annotation.JsonProperty("executionId")
    private final String executionId;

    @com.fasterxml.jackson.annotation.JsonProperty("commandId")
    private final String commandId;

    @com.fasterxml.jackson.annotation.JsonProperty("status")
    private final ResponseExecutionStatus status;

    @com.fasterxml.jackson.annotation.JsonProperty("cursor")
    private final String cursor;

    @com.fasterxml.jackson.annotation.JsonCreator
    public MessageReceiptVO(
            @com.fasterxml.jackson.annotation.JsonProperty("conversationId") String conversationId,
            @com.fasterxml.jackson.annotation.JsonProperty("messageId") String messageId,
            @com.fasterxml.jackson.annotation.JsonProperty("executionId") String executionId,
            @com.fasterxml.jackson.annotation.JsonProperty("commandId") String commandId,
            @com.fasterxml.jackson.annotation.JsonProperty("status") ResponseExecutionStatus status,
            @com.fasterxml.jackson.annotation.JsonProperty("cursor") String cursor) {
        this.conversationId = conversationId;
        this.messageId = messageId;
        this.executionId = executionId;
        this.commandId = commandId;
        this.status = status;
        this.cursor = cursor;
    }
}
