package com.agentsquad.squad.model.vo;

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
    private final Long conversationId;

    @com.fasterxml.jackson.annotation.JsonProperty("messageId")
    private final Long messageId;

    @com.fasterxml.jackson.annotation.JsonProperty("executionId")
    private final Long executionId;

    @com.fasterxml.jackson.annotation.JsonProperty("commandId")
    private final Long commandId;

    @com.fasterxml.jackson.annotation.JsonProperty("status")
    private final ResponseExecutionStatus status;

    @com.fasterxml.jackson.annotation.JsonProperty("cursor")
    private final String cursor;

    @com.fasterxml.jackson.annotation.JsonCreator
    public MessageReceiptVO(
            @com.fasterxml.jackson.annotation.JsonProperty("conversationId") Long conversationId,
            @com.fasterxml.jackson.annotation.JsonProperty("messageId") Long messageId,
            @com.fasterxml.jackson.annotation.JsonProperty("executionId") Long executionId,
            @com.fasterxml.jackson.annotation.JsonProperty("commandId") Long commandId,
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
