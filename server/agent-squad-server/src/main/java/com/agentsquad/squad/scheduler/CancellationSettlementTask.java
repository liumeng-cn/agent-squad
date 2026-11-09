package com.agentsquad.squad.scheduler;


import com.agentsquad.squad.service.ResponseExecutionService;
import com.agentsquad.squad.service.TaskFinalizerService;

import com.agentsquad.squad.mapper.CompletionMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 取消收尾定时任务
 *
 * <p>补查仍在取消中的目标和回复，等待原生执行及在途操作满足收尾条件。</p>
 */
@Component
@Slf4j
@ConditionalOnProperty(prefix = "agentsquad.delivery", name = "enabled", havingValue = "true")
public class CancellationSettlementTask {
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
    private com.agentsquad.squad.agent.session.NativeSessionGateway gateway;

    /**
     * 按游标分批检查待收尾记录，单条暂未收尾时继续检查本轮其他记录。
     */
    public void settle() {
        var tasks = mapper.cancellingTasks(afterId);
        if (tasks.isEmpty()) {
            afterId = 0;
            tasks = mapper.cancellingTasks(afterId);
        }
        for (var task : tasks) {
            afterId = task.getId();
            try {
                finalizer.finalizeCancelledTask(task.getUserId(), task.getTaskId());
            } catch (RuntimeException failure) {
                log.warn("取消核对暂未完成 taskId={} errorType={}", task.getTaskId(), failure.getClass().getSimpleName(), failure);
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
            afterBinding = target.getBindingId();
            try {
                if (gateway.quiescent(target)) {
                    responses.stopped(target.getUserId(), target.getBindingId());
                }
            } catch (RuntimeException failure) {
                log.warn("回复取消核对暂未完成 bindingId={} errorType={}", target.getBindingId(), failure.getClass().getSimpleName(), failure);
            }
        }
    }
}
