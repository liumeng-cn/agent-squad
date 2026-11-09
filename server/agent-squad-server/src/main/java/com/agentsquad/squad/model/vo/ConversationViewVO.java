package com.agentsquad.squad.model.vo;

import com.agentsquad.squad.model.bo.ConversationHeaderBO;

/**
 * 面向连续交流的会话视图。
 * <p>
 * 标题使用独立版本避免异步自动命名覆盖手动标题；最近活动时间由服务端提供。
 */
@lombok.Getter
@lombok.EqualsAndHashCode
@lombok.experimental.Accessors(fluent = true)
public class ConversationViewVO {

    @com.fasterxml.jackson.annotation.JsonProperty("conversationId")
    private final String conversationId;

    @com.fasterxml.jackson.annotation.JsonProperty("title")
    private final String title;

    @com.fasterxml.jackson.annotation.JsonProperty("titleSource")
    private final String titleSource;

    @com.fasterxml.jackson.annotation.JsonProperty("titleVersion")
    private final long titleVersion;

    @com.fasterxml.jackson.annotation.JsonProperty("generation")
    private final long generation;

    @com.fasterxml.jackson.annotation.JsonProperty("status")
    private final String status;

    @com.fasterxml.jackson.annotation.JsonProperty("version")
    private final long version;

    @com.fasterxml.jackson.annotation.JsonProperty("activeTaskId")
    private final String activeTaskId;

    @com.fasterxml.jackson.annotation.JsonProperty("lastActivityAt")
    private final String lastActivityAt;

    @com.fasterxml.jackson.annotation.JsonCreator
    public ConversationViewVO(
            @com.fasterxml.jackson.annotation.JsonProperty("conversationId") String conversationId,
            @com.fasterxml.jackson.annotation.JsonProperty("title") String title,
            @com.fasterxml.jackson.annotation.JsonProperty("titleSource") String titleSource,
            @com.fasterxml.jackson.annotation.JsonProperty("titleVersion") long titleVersion,
            @com.fasterxml.jackson.annotation.JsonProperty("generation") long generation,
            @com.fasterxml.jackson.annotation.JsonProperty("status") String status,
            @com.fasterxml.jackson.annotation.JsonProperty("version") long version,
            @com.fasterxml.jackson.annotation.JsonProperty("activeTaskId") String activeTaskId,
            @com.fasterxml.jackson.annotation.JsonProperty("lastActivityAt") String lastActivityAt) {
        this.conversationId = conversationId;
        this.title = title;
        this.titleSource = titleSource;
        this.titleVersion = titleVersion;
        this.generation = generation;
        this.status = status;
        this.version = version;
        this.activeTaskId = activeTaskId;
        this.lastActivityAt = lastActivityAt;
    }

    public static ConversationViewVO from(ConversationHeaderBO header) {
        return new ConversationViewVO(header.getConversationId(), header.getTitle(), header.getTitleSource(), header.getTitleVersion(),
                header.getGeneration(), header.getStatus(), header.getVersion(), header.getActiveTaskId(),
                header.getLastActivityAt().atOffset(java.time.ZoneOffset.UTC).toString());
    }
}
