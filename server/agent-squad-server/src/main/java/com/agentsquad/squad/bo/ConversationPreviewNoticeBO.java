package com.agentsquad.squad.bo;


/**
 * 跨实例公开正文预览
 * <p>
 * 仅传递经输出适配器筛选的有界可替换正文；没有持久游标，不代表正式交付，丢失后由终稿纠正。
 */
public record ConversationPreviewNoticeBO(int schemaVersion, String instanceId, String userId,
                                        String conversationId, ReplyPreviewBO preview) {
}
