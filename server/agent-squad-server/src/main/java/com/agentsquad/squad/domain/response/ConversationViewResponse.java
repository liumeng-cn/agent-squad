package com.agentsquad.squad.domain.response;

import com.agentsquad.squad.domain.entity.ConversationHeaderDO;

/**
 * 面向连续交流的会话视图。
 * <p>
 * 标题使用独立版本避免异步自动命名覆盖手动标题；最近活动时间由服务端提供。
 */
public record ConversationViewResponse(String conversationId, String title, String titleSource, long titleVersion,
                               long generation, String status, long version, String activeTaskId,
                               String lastActivityAt) {
    public static ConversationViewResponse from(ConversationHeaderDO header) {
        return new ConversationViewResponse(header.conversationId(), header.title(), header.titleSource(), header.titleVersion(),
                header.generation(), header.status(), header.version(), header.activeTaskId(),
                header.lastActivityAt().atOffset(java.time.ZoneOffset.UTC).toString());
    }
}
