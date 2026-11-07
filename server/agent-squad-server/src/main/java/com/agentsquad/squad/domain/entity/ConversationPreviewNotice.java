package com.agentsquad.squad.domain.entity;

import com.agentsquad.squad.domain.dto.ReplyPreview;

/**
 * 跨实例公开正文预览
 * <p>
 * 仅传递经输出适配器筛选的有界可替换正文；没有持久游标，不代表正式交付，丢失后由终稿纠正。
 */
public record ConversationPreviewNotice(int schemaVersion, String instanceId, String userId,
                                        String conversationId, ReplyPreview preview) {
}
