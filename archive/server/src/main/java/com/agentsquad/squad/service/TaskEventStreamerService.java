package com.agentsquad.squad.service;

import com.agentsquad.squad.exceptions.RuntimeBusinessException;
import com.agentsquad.squad.enums.RuntimeExceptionCode;

import com.agentsquad.squad.model.po.EventPO;
import com.agentsquad.squad.model.bo.TaskEventResponseBO;
import com.agentsquad.squad.mapper.TaskEventMapper;
import com.agentsquad.squad.mapper.TaskMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 任务公开事件 SSE 流；按任务事件序号顺序推送，断线重连由客户端负责。
 *
 * <p>共享有界调度线程读取持久事件；每条连接保留独立游标，断开后释放连接名额。</p>
 */
@Service
@Slf4j
public class TaskEventStreamerService {

    private static final long POLL_INTERVAL_MS = 1000L;
    private static final long HEARTBEAT_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(15);

    @Value("${agentsquad.events.poll-threads:4}")
    private int pollThreads;

    @Value("${agentsquad.events.max-connections:100}")
    private int maxConnections;

    @Resource
    private TaskEventMapper taskEventMapper;

    @Resource
    private TaskMapper taskMapper;

    private ScheduledThreadPoolExecutor pollExecutor;

    private Semaphore connectionSlots;

    @PostConstruct
    public void initialize() {
        if (pollThreads < 1 || maxConnections < 1) {
            throw new IllegalArgumentException("SSE 调度线程数和连接上限必须为正数");
        }
        pollExecutor = new ScheduledThreadPoolExecutor(pollThreads, runnable -> {
            Thread thread = new Thread(runnable, "task-event-poll");
            thread.setDaemon(true);
            return thread;
        });
        pollExecutor.setRemoveOnCancelPolicy(true);
        connectionSlots = new Semaphore(maxConnections);
    }

    @PreDestroy
    public void shutdown() {
        pollExecutor.shutdownNow();
    }

    /**
     * 建立 SSE 连接，从指定序号之后开始推送；校验任务归属与水位。
     */
    public SseEmitter stream(Long userId, Long taskId, long afterSeq) {
        if (taskMapper.findOwned(userId, taskId) == null) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.NOT_FOUND, "目标不存在");
        }
        long maxSeq = taskEventMapper.maxSeq(userId, taskId);
        if (afterSeq > maxSeq) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION, "事件游标超出当前水位");
        }
        if (!connectionSlots.tryAcquire()) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "当前事件连接已满，请稍后重试");
        }

        SseEmitter emitter = new SseEmitter(0L);
        AtomicLong lastSeq = new AtomicLong(afterSeq);
        AtomicLong lastHeartbeatAt = new AtomicLong(System.nanoTime());
        AtomicBoolean closed = new AtomicBoolean();
        AtomicReference<ScheduledFuture<?>> scheduledPoll = new AtomicReference<>();
        Runnable cleanup = () -> {
            if (closed.compareAndSet(false, true)) {
                ScheduledFuture<?> future = scheduledPoll.get();
                if (future != null) {
                    future.cancel(false);
                }
                connectionSlots.release();
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
                List<EventPO> rows = taskEventMapper.listAfter(userId, taskId, lastSeq.get());
                for (EventPO row : rows) {
                    emitter.send(SseEmitter.event()
                            .name("message")
                            .id(taskId + ":" + row.getEventSeq())
                            .data(toResponse(row), MediaType.APPLICATION_JSON));
                    lastSeq.set(row.getEventSeq());
                }
                long currentTime = System.nanoTime();
                if (currentTime - lastHeartbeatAt.get() >= HEARTBEAT_INTERVAL_NANOS) {
                    emitter.send(SseEmitter.event().comment("heartbeat"));
                    lastHeartbeatAt.set(currentTime);
                }
            } catch (IOException exception) {
                log.debug("SSE 连接已断开 taskId={}", taskId);
                cleanup.run();
            } catch (Exception exception) {
                log.warn("SSE 推送异常 taskId={}", taskId, exception);
                emitter.completeWithError(exception);
                cleanup.run();
            }
        };
        try {
            ScheduledFuture<?> future = pollExecutor.scheduleWithFixedDelay(
                    poll, 0, POLL_INTERVAL_MS, TimeUnit.MILLISECONDS);
            scheduledPoll.set(future);
            if (closed.get()) {
                future.cancel(false);
            }
        } catch (RejectedExecutionException exception) {
            cleanup.run();
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "事件服务正在关闭，请稍后重试");
        }
        return emitter;
    }

    private TaskEventResponseBO toResponse(EventPO row) {
        return new TaskEventResponseBO(row.getEventSeq(), row.getTaskId(), row.getEventType(),
                row.getPayloadJson(), row.getOccurredAt());
    }
}
