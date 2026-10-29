package com.agentsquad.squad.conversation.service;

import com.agentsquad.squad.conversation.repository.TaskDirectiveMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 修订协调的公平扫描入口。
 * <p>
 * 每条指令独立事务与退避；等待中的操作不刷日志，不阻塞其他会话。
 */
@Component
@Slf4j
@ConditionalOnProperty(prefix = "agentsquad.delivery", name = "enabled", havingValue = "true")
public class TaskDirectiveScheduler {
    @Resource
    private TaskDirectiveMapper mapper;

    @Resource
    private TaskDirectiveService service;

    @Scheduled(fixedDelayString = "${agentsquad.directive.poll-delay-ms:1000}")
    public void dispatchDue() {
        for (var candidate : mapper.due(LocalDateTime.now(ZoneOffset.UTC))) {
            try {
                service.dispatch(candidate);
            } catch (Exception exception) {
                log.warn("目标修改协调检查失败 directiveId={} taskId={} errorType={}", candidate.directiveId(), candidate.taskId(), exception.getClass().getSimpleName(), exception);
            }
        }
    }
}
