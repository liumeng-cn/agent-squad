package com.agentsquad.squad.presentation.stream;

import com.agentsquad.squad.model.bo.ConversationEventBO;
import com.agentsquad.squad.model.bo.ReplyPreviewBO;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * SSE发送结果记录器
 * <p>
 * 只记录公开事件对象供断言，不启动HTTP请求或业务执行。
 */
final class RecordingSseEmitter extends SseEmitter {
    final List<ConversationEventBO> events = new CopyOnWriteArrayList<>();
    final List<ReplyPreviewBO> previews = new CopyOnWriteArrayList<>();
    final AtomicInteger completions = new AtomicInteger();

    @Override
    public void send(SseEventBuilder builder) {
        for (var item : builder.build()) {
            if (item.getData() instanceof ConversationEventBO event) {
                events.add(event);
            } else if (item.getData() instanceof ReplyPreviewBO preview) {
                previews.add(preview);
            }
        }
    }

    @Override
    public void complete() {
        completions.incrementAndGet();
    }
}
