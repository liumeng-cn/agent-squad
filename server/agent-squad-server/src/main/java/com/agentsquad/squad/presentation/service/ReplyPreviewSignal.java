package com.agentsquad.squad.presentation.service;

import com.agentsquad.squad.presentation.dto.ReplyPreview;

/**
 * 临时公开正文的内存通知
 * <p>
 * 无订阅者时可丢失，任何消费者都不能把预览当作持久终稿或执行完成证据。
 */
public record ReplyPreviewSignal(String userId, String conversationId, ReplyPreview preview) {
}
