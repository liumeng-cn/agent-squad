package com.agentsquad.squad.presentation.service;

import com.agentsquad.squad.common.exception.RuntimeBusinessException;
import com.agentsquad.squad.common.exception.RuntimeExceptionCode;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 会话公开事实的有界SSE订阅服务。
 * <p>
 * 每次轮询重验归属与代际，只读取持久水位之后的公开事件；断开连接不改变运行状态。
 */
@Service
@Slf4j
public class ConversationEventStreamer {
    @Resource
    private ConversationQueryService queries;

    @Value("${agentsquad.events.poll-threads:4}")
    private int pollThreads;

    @Value("${agentsquad.events.max-connections:100}")
    private int maxConnections;
    private ScheduledThreadPoolExecutor executor;
    private Semaphore slots;

    @PostConstruct
    public void initialize() {
        if (pollThreads < 1 || maxConnections < 1) {
            throw new IllegalArgumentException("会话订阅配置无效");
        }
        slots = new Semaphore(maxConnections);
        executor = new ScheduledThreadPoolExecutor(pollThreads, runnable -> {
            Thread thread = new Thread(runnable, "conversation-event-poll");
            thread.setDaemon(true);
            return thread;
        });
        executor.setRemoveOnCancelPolicy(true);
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }

    /**
     * 先校验游标再占用名额；清理以原子标记保证完成、异常和超时只释放一次。
     */
    public SseEmitter stream(String userId, String conversationId, String cursor) {
        queries.events(userId, conversationId, cursor);
        if (!slots.tryAcquire()) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "连接暂时繁忙，请稍后重试");
        }
        SseEmitter emitter = new SseEmitter(0L);
        AtomicReference<String> after = new AtomicReference<>(cursor);
        AtomicBoolean closed = new AtomicBoolean();
        AtomicLong heartbeat = new AtomicLong(System.nanoTime());
        AtomicReference<ScheduledFuture<?>> future = new AtomicReference<>();
        Runnable cleanup = () -> {
            if (closed.compareAndSet(false, true)) {
                var scheduled = future.get();
                if (scheduled != null) {
                    scheduled.cancel(false);
                }
                slots.release();
            }
        };
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(error -> cleanup.run());
        Runnable poll = () -> {
            if (closed.get()) {
                return;
            }
            try {
                for (var event : queries.events(userId, conversationId, after.get())) {
                    String next = queries.eventCursor(event);
                    emitter.send(SseEmitter.event().name("message").id(next).data(event, MediaType.APPLICATION_JSON));
                    after.set(next);
                }
                if (System.nanoTime() - heartbeat.get() > TimeUnit.SECONDS.toNanos(15)) {
                    emitter.send(SseEmitter.event().comment("heartbeat"));
                    heartbeat.set(System.nanoTime());
                }
            } catch (IOException exception) {
                cleanup.run();
            } catch (Exception exception) {
                log.debug("会话订阅结束 conversationId={} reason={}", conversationId, exception.getClass().getSimpleName());
                emitter.completeWithError(exception);
                cleanup.run();
            }
        };
        try {
            future.set(executor.scheduleWithFixedDelay(poll, 0, 500, TimeUnit.MILLISECONDS));
            if (closed.get()) {
                future.get().cancel(false);
            }
        } catch (java.util.concurrent.RejectedExecutionException exception) {
            cleanup.run();
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "订阅服务正在关闭，请稍后重试");
        }
        return emitter;
    }
}
