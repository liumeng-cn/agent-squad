package com.agentsquad.squad.v2.service;

import com.agentsquad.squad.v2.agent.AgentScopeRunner;
import com.agentsquad.squad.v2.agent.SquadAgentRegistry;
import com.agentsquad.squad.v2.bo.SquadExecutionBO;
import com.agentsquad.squad.v2.config.properties.SquadV2AgentProperties;
import com.agentsquad.squad.v2.config.properties.SquadV2ExecutionProperties;
import com.agentsquad.squad.v2.enums.SquadExecutionStatus;
import com.agentsquad.squad.v2.event.SquadEventBuffer;
import com.agentsquad.squad.v2.event.SquadEventPublisher;
import com.agentsquad.squad.v2.utils.SquadIdUtils;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.agent.AgentRun;
import io.agentscope.core.event.AgentEvent;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/** V2 执行管理器，管理请求级上下文、执行归属和断线补收。 */
@Slf4j
@Service
public class SquadExecutionManager {
    @Resource private SquadV2AgentProperties agentProperties;
    @Resource private SquadV2ExecutionProperties executionProperties;
    @Resource private AgentScopeRunner runner;
    @Resource private SquadEventPublisher publisher;
    private final Map<Long, SquadExecutionBO> executions = new ConcurrentHashMap<>();

    /** 创建新执行并启动后台 AgentRun。 */
    public SquadExecutionBO submit(Long userId, Long conversationId, String message) {
        Long actualConversationId = conversationId == null ? SquadIdUtils.nextSnowflakeId() : conversationId;
        Long executionId = SquadIdUtils.nextSnowflakeId();
        String runId = SquadIdUtils.uuidWithoutHyphens();
        RuntimeContext context = RuntimeContext.builder().userId(String.valueOf(userId))
            .sessionId(String.valueOf(actualConversationId)).runId(runId).build();
        SquadExecutionBO execution = new SquadExecutionBO();
        execution.setExecutionId(executionId);
        execution.setConversationId(actualConversationId);
        execution.setUserId(userId);
        execution.setRunId(runId);
        execution.setRuntimeContext(context);
        execution.setEventBuffer(new SquadEventBuffer(executionProperties.getEventBufferCapacity()));
        execution.setAgentRegistry(new SquadAgentRegistry(executionId,
            agentProperties.getAgentId(), agentProperties.getAgentName()));
        execution.setCreatedAt(Instant.now());
        if (executions.putIfAbsent(executionId, execution) != null) {
            throw new IllegalStateException("Snowflake 执行 ID 冲突");
        }
        publisher.publishTurnStarted(execution);
        runner.start(execution, message);
        return execution;
    }

    /** 恢复事件流前校验执行归属。 */
    public SquadExecutionBO require(Long executionId, Long userId) {
        SquadExecutionBO execution = executions.get(executionId);
        if (execution == null || !userId.equals(execution.getUserId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "执行记录不存在或已过期");
        }
        return execution;
    }

    /** 清理过期终态执行；不会触碰仍运行的 AgentRun。 */
    @Scheduled(fixedDelay = 60000L)
    public void cleanupCompletedExecutions() {
        Instant cutoff = Instant.now().minus(executionProperties.getCompletedRetentionMinutes(), ChronoUnit.MINUTES);
        executions.entrySet().removeIf(entry -> {
            SquadExecutionBO execution = entry.getValue();
            return execution.isTerminal() && execution.getFinishedAt() != null
                && execution.getFinishedAt().isBefore(cutoff);
        });
    }

    /** 应用关闭时停止本机运行中的 AgentRun。 */
    @PreDestroy
    public void shutdown() {
        for (SquadExecutionBO execution : executions.values()) {
            if (execution.isTerminal()) continue;
            AgentRun<AgentEvent> run = execution.getAgentRun();
            if (run != null) run.cancel();
            else if (execution.markCancelled()) {
                publisher.publishTurnCancelled(execution);
                execution.getEventBuffer().complete();
            }
        }
        log.info("V2 执行管理器关闭，已请求取消未结束执行");
    }
}