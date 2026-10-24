package com.agentsquad.squad.integration.agentscope;

import com.agentsquad.squad.runtime.domain.ProjectionTarget;
import com.agentsquad.squad.runtime.repository.ProjectionMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 按持久水位从原生 Journal 读取稳定前缀，逐条交给事务投影器。
 */
@Component
@Slf4j
@ConditionalOnProperty(prefix = "agentsquad.delivery", name = "enabled", havingValue = "true")
public class NativeProjectionPoller {

    @Resource
    private ProjectionMapper projectionMapper;

    @Resource
    private NativeSessionGateway nativeSessionGateway;

    @Resource
    private NativeProjectionService projectionService;

    /**
     * 每个 Binding 独立失败与退避，不阻塞其他原生会话。
     */
    @Scheduled(fixedDelayString = "${agentsquad.projection.poll-delay-ms:1000}")
    public void projectDue() {
        for (ProjectionTarget target : projectionMapper.findDueTargets(now())) {
            try {
                List<NativeEventView> events = nativeSessionGateway.readEvents(target, target.cursor(), 100);
                for (NativeEventView event : events) {
                    projectionService.accept(target, event);
                }
                projectionMapper.defer(target.userId(), target.bindingId(),
                        now().plusSeconds(events.isEmpty() ? 5 : 1));
            } catch (Exception exception) {
                projectionMapper.defer(target.userId(), target.bindingId(), now().plusSeconds(10));
                log.warn("原生日志投影待重试 bindingId={} taskId={} errorType={}",
                        target.bindingId(), target.taskId(), exception.getClass().getSimpleName(), exception);
            }
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}
