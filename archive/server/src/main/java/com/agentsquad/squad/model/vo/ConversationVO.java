package com.agentsquad.squad.model.vo;

/**
 * 对话公开响应。
 */
@lombok.Getter
@lombok.EqualsAndHashCode
@lombok.experimental.Accessors(fluent = true)
public class ConversationVO {

    @com.fasterxml.jackson.annotation.JsonProperty("conversationId")
    private final Long conversationId;

    @com.fasterxml.jackson.annotation.JsonProperty("title")
    private final String title;

    @com.fasterxml.jackson.annotation.JsonProperty("generation")
    private final long generation;

    @com.fasterxml.jackson.annotation.JsonProperty("status")
    private final String status;

    @com.fasterxml.jackson.annotation.JsonProperty("version")
    private final long version;

    @com.fasterxml.jackson.annotation.JsonProperty("activeTaskId")
    private final Long activeTaskId;

    @com.fasterxml.jackson.annotation.JsonCreator
    public ConversationVO(
            @com.fasterxml.jackson.annotation.JsonProperty("conversationId") Long conversationId,
            @com.fasterxml.jackson.annotation.JsonProperty("title") String title,
            @com.fasterxml.jackson.annotation.JsonProperty("generation") long generation,
            @com.fasterxml.jackson.annotation.JsonProperty("status") String status,
            @com.fasterxml.jackson.annotation.JsonProperty("version") long version,
            @com.fasterxml.jackson.annotation.JsonProperty("activeTaskId") Long activeTaskId) {
        this.conversationId = conversationId;
        this.title = title;
        this.generation = generation;
        this.status = status;
        this.version = version;
        this.activeTaskId = activeTaskId;
    }
}
