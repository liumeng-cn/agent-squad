package com.agentsquad.squad.integration.agentscope;

import com.agentsquad.squad.conversation.domain.ReplyPreviewTarget;
import com.agentsquad.squad.presentation.dto.ReplyPreview;
import com.agentsquad.squad.presentation.service.ReplyPreviewSignal;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * 单次模型调用的有界公开文本缓冲
 * <p>
 * 只接收已判定为TextBlockDelta的公开文本，不解析工具参数或推理；按时间合并并用版本化全文替换防止丢块拼错。
 */
public final class ReplyPreviewBuffer {
    private static final int MAX_TEXT_UNITS = 65536;
    private static final long MIN_INTERVAL_NANOS = 50000000;
    private final ReplyPreviewTarget target;
    private final String runId;
    private final String streamId = UUID.randomUUID().toString();
    private final Consumer<ReplyPreviewSignal> publish;
    private final StringBuilder text = new StringBuilder();
    private long revision;
    private long lastSent;
    private int sentLength;

    public ReplyPreviewBuffer(ReplyPreviewTarget target, String runId, Consumer<ReplyPreviewSignal> publish) {
        this.target = target;
        this.runId = runId;
        this.publish = publish;
    }

    public void append(String delta) {
        if (delta == null || delta.isEmpty() || text.length() >= MAX_TEXT_UNITS) {
            return;
        }
        int count = Math.min(delta.length(), MAX_TEXT_UNITS - text.length());
        if (count < delta.length() && count > 0 && Character.isHighSurrogate(delta.charAt(count - 1))) {
            count--;
        }
        text.append(delta, 0, count);
        if (System.nanoTime() - lastSent >= MIN_INTERVAL_NANOS) {
            flush();
        }
    }

    public void flush() {
        int length = text.length();
        if (length > 0 && Character.isHighSurrogate(text.charAt(length - 1))) {
            length--;
        }
        if (length <= sentLength) {
            return;
        }
        publish.accept(new ReplyPreviewSignal(target.userId(), target.conversationId(),
                new ReplyPreview(2, target.executionId(), target.sourceMessageId(), runId, streamId,
                        ++revision, 0, text.substring(0, length), true, true)));
        sentLength = length;
        lastSent = System.nanoTime();
    }
}
