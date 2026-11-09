package com.agentsquad.squad.service;

import com.agentsquad.squad.model.bo.ReplyPreviewTargetBO;
import com.agentsquad.squad.enums.ReplyPreviewPhase;
import com.agentsquad.squad.model.bo.ReplyPreviewBO;
import com.agentsquad.squad.event.ReplyPreviewSignal;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * 单次模型调用的有界公开文本缓冲
 * <p>
 * 只接收已判定为公开正文或推理进展的增量，不解析工具参数或推理内容；按时间合并并用版本化全文替换防止丢块拼错。
 * 推理阶段只上报阶段心跳，推理正文不上送页面。
 */
public final class ReplyPreviewBuffer {
    private static final int MAX_TEXT_UNITS = 65536;
    private static final long MIN_INTERVAL_NANOS = 50000000;
    private static final long THINKING_INTERVAL_NANOS = 2000000000;
    private final ReplyPreviewTargetBO target;
    private final String runId;
    private final String streamId = UUID.randomUUID().toString();
    private final Consumer<ReplyPreviewSignal> publish;
    private final StringBuilder text = new StringBuilder();
    private long revision;
    private long lastSent;
    private long lastThinkingSent;
    private int sentLength;
    private boolean closed;

    public ReplyPreviewBuffer(ReplyPreviewTargetBO target, String runId, Consumer<ReplyPreviewSignal> publish) {
        this.target = target;
        this.runId = runId;
        this.publish = publish;
    }

    public synchronized void append(String delta) {
        if (closed || delta == null || delta.isEmpty() || text.length() >= MAX_TEXT_UNITS) {
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

    /**
     * 推理增量只表达“仍在处理”，按固定间隔合并；不累计、不转发推理内容。
     */
    public synchronized void thinking() {
        if (closed) {
            return;
        }
        long now = System.nanoTime();
        if (lastThinkingSent != 0 && now - lastThinkingSent < THINKING_INTERVAL_NANOS) {
            return;
        }
        lastThinkingSent = now;
        emit(ReplyPreviewPhase.THINKING);
    }

    public synchronized void flush() {
        if (closed) {
            return;
        }
        int length = text.length();
        if (length > 0 && Character.isHighSurrogate(text.charAt(length - 1))) {
            length--;
        }
        if (length <= sentLength) {
            return;
        }
        emit(ReplyPreviewPhase.ANSWERING);
        sentLength = length;
        lastSent = System.nanoTime();
    }

    /** 失败或取消只撤销本次临时流；保留稳定流身份，迟到增量不能重新激活旧草稿。 */
    public synchronized void abort() {
        if (!closed) {
            closed = true;
            text.setLength(0);
            emit(ReplyPreviewPhase.ABORTED);
        }
    }

    public synchronized void complete() {
        flush();
        closed = true;
    }

    private void emit(ReplyPreviewPhase phase) {
        int length = text.length();
        if (length > 0 && Character.isHighSurrogate(text.charAt(length - 1))) {
            length--;
        }
        publish.accept(new ReplyPreviewSignal(target.getUserId(), target.getConversationId(),
                new ReplyPreviewBO(2, target.getExecutionId(), target.getSourceMessageId(), runId, streamId,
                        ++revision, 0, text.substring(0, length), phase, true, true)));
    }
}
