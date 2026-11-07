package com.agentsquad.squad.service;

import com.agentsquad.squad.mapper.CompletionMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 等待任务真正停止后完成取消
 * <p>
 * 原生日志结束或未知操作核对可能晚于停止命令；独立补查避免没有新事件时永久停在取消中。
 */
@Component
@Slf4j
@ConditionalOnProperty(prefix = "agentsquad.delivery", name = "enabled", havingValue = "true")
public class CancellationSettlementSchedulerService {
    private long afterId;
    private String afterBinding = "";

    @Resource
    private CompletionMapper mapper;

    @Resource
    private TaskFinalizerService finalizer;

    @Resource
    private com.agentsquad.squad.mapper.ResponseRuntimeMapper replies;

    @Resource
    private com.agentsquad.squad.service.ResponseExecutionService responses;

    @Resource
    private com.agentsquad.squad.integration.agentscope.NativeSessionGateway gateway;

    @Scheduled(fixedDelayString = "${agentsquad.cancel.settle-delay-ms:3000}")
    public void settle() {
        var tasks = mapper.cancellingTasks(afterId);
        if (tasks.isEmpty()) {
            afterId = 0;
            tasks = mapper.cancellingTasks(afterId);
        }
        for (var task : tasks) {
            afterId = task.id();
            try {
                finalizer.finalizeCancelledTask(task.userId(), task.taskId());
            } catch (RuntimeException failure) {
                log.warn("取消核对暂未完成 taskId={} errorType={}", task.taskId(), failure.getClass().getSimpleName(), failure);
            }
        }
        settleReplies();
    }

    /**
     * 回复取消与独立任务分开扫描；停止一条回答不能影响另一个执行。
     */
    private void settleReplies() {
        var targets = replies.cancellationTargets(afterBinding);
        if (targets.isEmpty()) {
            afterBinding = "";
            targets = replies.cancellationTargets(afterBinding);
        }
        for (var target : targets) {
            afterBinding = target.bindingId();
            try {
                if (gateway.quiescent(target)) {
                    responses.stopped(target.userId(), target.bindingId());
                }
            } catch (RuntimeException failure) {
                log.warn("回复取消核对暂未完成 bindingId={} errorType={}", target.bindingId(), failure.getClass().getSimpleName(), failure);
            }
        }
    }
}
