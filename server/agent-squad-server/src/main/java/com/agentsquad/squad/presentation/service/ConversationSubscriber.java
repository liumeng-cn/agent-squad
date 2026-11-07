package com.agentsquad.squad.presentation.service;

import com.agentsquad.squad.domain.dto.ConversationEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 独立的页面事件订阅
 * <p>
 * 队列有界且只控制本连接发送，断开只释放订阅；从不引用或取消Worker、模型或后台任务。
 */
@Slf4j
public final class ConversationSubscriber {
    private static final int BUFFER_LIMIT = 128;
    private final SseEmitter emitter;
    private final Executor sender;
    private final Runnable onClosed;
    private final Runnable authorize;
    private final ArrayBlockingQueue<ConversationEvent> queue = new ArrayBlockingQueue<>(BUFFER_LIMIT);
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean sending = new AtomicBoolean();
    private final AtomicBoolean heartbeatPending = new AtomicBoolean();
    private final java.util.concurrent.ConcurrentHashMap<String, com.agentsquad.squad.domain.dto.ReplyPreview> previews = new java.util.concurrent.ConcurrentHashMap<>();
    private volatile String cursor;

    public ConversationSubscriber(SseEmitter emitter, String cursor, Executor sender, Runnable onClosed, Runnable authorize) {
        this.emitter = emitter;
        this.cursor = cursor;
        this.sender = sender;
        this.onClosed = onClosed;
        this.authorize = authorize;
        emitter.onCompletion(this::close);
        emitter.onTimeout(this::close);
        emitter.onError(error -> close());
    }

    public String cursor() {
        return cursor;
    }

    public boolean isClosed() {
        return closed.get();
    }

    public synchronized void offer(ConversationEvent event) {
        if (closed.get()) {
            return;
        }
        long previous = Long.parseLong(cursor.substring(cursor.lastIndexOf(':') + 1));
        if (event.sequence() <= previous) {
            return;
        }
        if (event.sequence() != previous + 1 || !queue.offer(event)) {
            close();
            return;
        }
        cursor = event.conversationId() + ":" + event.generation() + ":" + event.sequence();
        schedule();
    }

    /**
     * 每个回复只保留最新可替换预览，慢连接不会积累逐token队列。
     */
    public void preview(com.agentsquad.squad.domain.dto.ReplyPreview preview) {
        if (closed.get()) {
            return;
        }
        if (previews.size() >= 8 && !previews.containsKey(preview.executionId())) {
            close();
            return;
        }
        // Redis监听回调可能并行，低版本思考帧不能覆盖同一流待发送的正文。
        previews.compute(preview.executionId(), (id, pending) -> pending != null
                && pending.streamId().equals(preview.streamId()) && pending.revision() >= preview.revision()
                ? pending : preview);
        schedule();
    }

    public void heartbeat() {
        heartbeatPending.set(true);
        schedule();
    }

    private void schedule() {
        if (closed.get() || !sending.compareAndSet(false, true)) {
            return;
        }
        try {
            sender.execute(this::drain);
        } catch (RejectedExecutionException busy) {
            sending.set(false);
            close();
        }
    }

    private void drain() {
        try {
            authorize.run();
            ConversationEvent event;
            while (!closed.get() && (event = queue.poll()) != null) {
                emitter.send(SseEmitter.event().name("message")
                        .id(event.conversationId() + ":" + event.generation() + ":" + event.sequence())
                        .data(event, MediaType.APPLICATION_JSON));
            }
            for (var entry : previews.entrySet()) {
                if (closed.get()) {
                    break;
                }
                if (previews.remove(entry.getKey(), entry.getValue())) {
                    emitter.send(SseEmitter.event().name("output.preview").data(entry.getValue(), MediaType.APPLICATION_JSON));
                }
            }
            if (!closed.get() && heartbeatPending.getAndSet(false)) {
                emitter.send(SseEmitter.event().comment("heartbeat"));
            }
        } catch (IOException | RuntimeException disconnected) {
            close();
        } finally {
            sending.set(false);
            if (!closed.get() && (!queue.isEmpty() || !previews.isEmpty() || heartbeatPending.get())) {
                schedule();
            }
        }
    }

    public void close() {
        if (closed.compareAndSet(false, true)) {
            queue.clear();
            previews.clear();
            onClosed.run();
            try {
                emitter.complete();
            } catch (IllegalStateException clientGone) {
                // 客户端已断开导致 AsyncContext 失效属正常取消，只留调试痕迹。
                log.debug("事件订阅连接已关闭");
            }
        }
    }
}
