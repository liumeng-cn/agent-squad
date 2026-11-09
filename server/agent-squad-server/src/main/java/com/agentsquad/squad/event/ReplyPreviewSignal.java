package com.agentsquad.squad.event;

import com.agentsquad.squad.model.bo.ReplyPreviewBO;

/**
 * 临时公开正文的内存通知
 * <p>
 * 无订阅者时可丢失，任何消费者都不能把预览当作持久终稿或执行完成证据。
 */
public record ReplyPreviewSignal(Long userId, Long conversationId, ReplyPreviewBO preview) {
}
