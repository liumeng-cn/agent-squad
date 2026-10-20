package com.agentsquad.squad.runtime.service;

import com.agentsquad.squad.runtime.domain.EventRow;
import com.agentsquad.squad.runtime.dto.TaskEventResponse;
import com.agentsquad.squad.runtime.repository.TaskEventMapper;
import com.agentsquad.squad.runtime.repository.TaskMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 任务公开事件 SSE 流；按存储主键序顺序推送，断线不重连由客户端负责。
 *
 * <p>当前使用数据库轮询补偿实时通知；不依赖推送中间件。发送失败或客户端断开时清理调度线程。</p>
 */
@Service
@Slf4j
public class TaskEventStreamer {

    private static final long POLL_INTERVAL_MS = 1000L;

    @Resource
    private TaskEventMapper taskEventMapper;

    @Resource
    private TaskMapper taskMapper;

    /**
     * 建立 SSE 连接，从指定序号之后开始推送；校验任务归属与水位。
     */
    public SseEmitter stream(String userId, String taskId, long afterSeq) {
        if (taskMapper.findOwned(userId, taskId) == null) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.NOT_FOUND, "目标不存在");
        }
        long maxSeq = taskEventMapper.maxSeq(userId, taskId);
        if (afterSeq > maxSeq) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION, "事件游标超出当前水位");
        }
        SseEmitter emitter = new SseEmitter(0L);
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "sse-" + taskId);
            thread.setDaemon(true);
            return thread;
        });
        AtomicLong lastSeq = new AtomicLong(afterSeq);
        Runnable poll = () -> {
            try {
                List<EventRow> rows = taskEventMapper.listAfter(userId, taskId, lastSeq.get());
                for (EventRow row : rows) {
                    emitter.send(SseEmitter.event()
                            .name("message")
                            .id(taskId + ":" + row.eventSeq())
                            .data(toResponse(row), MediaType.APPLICATION_JSON));
                    lastSeq.set(row.eventSeq());
                }
            } catch (IOException exception) {
                log.debug("SSE 连接已断开 taskId={}", taskId);
                stop(executor);
            } catch (Exception exception) {
                log.warn("SSE 推送异常 taskId={}", taskId, exception);
                emitter.completeWithError(exception);
                stop(executor);
            }
        };
        ScheduledFuture<?> future = executor.scheduleAtFixedRate(
                poll, 0, POLL_INTERVAL_MS, TimeUnit.MILLISECONDS);
        Runnable cleanup = () -> {
            future.cancel(true);
            stop(executor);
        };
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(error -> cleanup.run());
        return emitter;
    }

    private void stop(ScheduledExecutorService executor) {
        executor.shutdownNow();
    }

    private TaskEventResponse toResponse(EventRow row) {
        return new TaskEventResponse(row.eventSeq(), row.taskId(), row.eventType(),
                row.payloadJson(), row.occurredAt());
    }
}
