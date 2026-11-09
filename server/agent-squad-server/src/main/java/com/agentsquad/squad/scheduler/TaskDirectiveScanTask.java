package com.agentsquad.squad.scheduler;


import com.agentsquad.squad.service.TaskDirectiveService;

import com.agentsquad.squad.mapper.TaskDirectiveMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 目标修订扫描定时任务
 *
 * <p>扫描到期的目标修订，逐条交给修订服务协调。</p>
 */
@Component
@Slf4j
@ConditionalOnProperty(prefix = "agentsquad.delivery", name = "enabled", havingValue = "true")
public class TaskDirectiveScanTask {
    @Resource
    private TaskDirectiveMapper mapper;

    @Resource
    private TaskDirectiveService service;

    /**
     * 按当前时间查询到期修订，单条协调异常时继续处理本轮其他修订。
     */
    public void dispatchDue() {
        for (var candidate : mapper.due(LocalDateTime.now(ZoneOffset.UTC))) {
            try {
                service.dispatch(candidate);
            } catch (Exception exception) {
                log.warn("目标修改协调检查失败 directiveId={} taskId={} errorType={}", candidate.getDirectiveId(), candidate.getTaskId(), exception.getClass().getSimpleName(), exception);
            }
        }
    }
}
