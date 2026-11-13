package com.agentsquad.squad.v2.agent;

import com.agentsquad.squad.v2.bo.SquadExecutionBO;
import com.agentsquad.squad.v2.event.SquadEventPublisher;
import io.agentscope.core.agent.AgentRun;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.message.UserMessage;
import io.agentscope.harness.agent.HarnessAgent;
import jakarta.annotation.Resource;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import reactor.core.scheduler.Schedulers;

/** AgentScope 执行入口；SSE 断开不会取消后台 AgentRun。 */
@Slf4j
@Component
public class AgentScopeRunner {
    @Resource private ObjectProvider<HarnessAgent> harnessAgentProvider;
    @Resource private SquadEventPublisher eventPublisher;
    /** 使用单例 HarnessAgent 启动一次请求级 AgentRun。 */
    public void start(SquadExecutionBO execution, String message) {
        try {
            HarnessAgent agent = harnessAgentProvider.getIfAvailable();
            if (agent == null) throw new IllegalStateException(
                "V2 Agent 未启用，请配置 SQUAD_V2_AGENT_ENABLED=true 和 SQUAD_V2_AGENT_API_KEY");
            AgentRun<AgentEvent> run = agent.prepareRun(List.of(new UserMessage(message)),
                execution.getRuntimeContext());
            execution.setAgentRun(run);
            execution.markRunning();
            run.stream().subscribeOn(Schedulers.boundedElastic()).subscribe(
                event -> eventPublisher.publishNative(execution, event),
                error -> finishFailure(execution, error),
                () -> finishSuccess(execution));
        } catch (RuntimeException error) {
            finishFailure(execution, error);
        }
    }
    private void finishSuccess(SquadExecutionBO execution) {
        try {
            if (execution.markCompleted()) eventPublisher.publishTurnCompleted(execution);
        } finally { execution.getEventBuffer().complete(); }
    }
    private void finishFailure(SquadExecutionBO execution, Throwable error) {
        try {
            if (execution.markFailed()) {
                log.error("V2 AgentScope 执行失败，executionId={}，原因={}",
                    execution.getExecutionId(), error.getMessage(), error);
                eventPublisher.publishTurnFailed(execution, error);
            }
        } finally { execution.getEventBuffer().complete(); }
    }
}