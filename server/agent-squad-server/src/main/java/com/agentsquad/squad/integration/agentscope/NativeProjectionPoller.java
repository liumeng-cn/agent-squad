package com.agentsquad.squad.integration.agentscope;

import com.agentsquad.squad.runtime.domain.ProjectionTarget;
import com.agentsquad.squad.runtime.repository.ProjectionMapper;
import com.agentsquad.squad.runtime.domain.MasterAgentIdentity;
import com.agentsquad.squad.runtime.service.WorkerRecoveryService;
import jakarta.annotation.Resource;
import com.agentsquad.squad.conversation.service.ResponseExecutionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 从上次处理到的事件序号继续读取 AgentScope 执行事件，并依次更新业务状态。
 */
@Component
@Slf4j
@ConditionalOnProperty(prefix = "agentsquad.delivery", name = "enabled", havingValue = "true")
public class NativeProjectionPoller {

    @Resource
    private com.agentsquad.squad.runtime.service.ExecutionAuthorizationService authorization;

    @Resource
    private com.agentsquad.squad.runtime.service.ExecutionRestrictionService restrictions;

    @Resource
    private com.agentsquad.squad.conversation.repository.ExecutionScopeMapper scopeMapper;

    @Resource
    private com.agentsquad.squad.runtime.service.ExecutionFailureService failures;

    @Resource
    private ResponseExecutionService responses;

    @Resource
    private ProjectionMapper projectionMapper;

    @Resource
    private NativeSessionGateway nativeSessionGateway;

    @Resource
    private NativeProjectionService projectionService;

    @Resource
    private WorkerRecoveryService workerRecoveryService;

    /**
     * 每个 Agent 会话各自处理失败和重试，不影响其他会话。
     */
    @Scheduled(fixedDelayString = "${agentsquad.projection.poll-delay-ms:1000}")
    public void projectDue() {
        for (ProjectionTarget target : projectionMapper.findDueTargets(now())) {
            try {
                authorization.requireBindingCurrent(target.userId(), target.bindingId());
                List<NativeEventView> events = nativeSessionGateway.readEvents(target, target.cursor(), 100);
                for (NativeEventView event : events) {
                    projectionService.accept(target, event);
                }
                if (target.taskId() == null) {
                    String commandId = responses.originalCommand(target);
                    if (commandId != null) {
                        NativeSubmissionReceipt receipt = nativeSessionGateway.inspectTask(target, commandId);
                        if (commandId.equals(receipt.commandId())) {
                            responses.recover(target, receipt);
                        }
                    }
                } else if (!MasterAgentIdentity.AGENT_ID.equals(target.agentId())) {
                    String commandId = workerRecoveryService.originalSubmitCommandId(target);
                    if (commandId != null) {
                        NativeSubmissionReceipt receipt = nativeSessionGateway.inspectTask(target, commandId);
                        if (commandId.equals(receipt.commandId())) {
                            workerRecoveryService.queueInterrupted(target, receipt);
                        }
                    }
                } else {
                    String commandId = failures.masterCommand(target);
                    if (commandId != null) {
                        NativeSubmissionReceipt receipt = nativeSessionGateway.inspectTask(target, commandId);
                        if (commandId.equals(receipt.commandId())) { failures.recoverInterrupted(target, receipt); }
                    }
                }
                projectionMapper.defer(target.userId(), target.bindingId(),
                        now().plusSeconds(events.isEmpty() ? 5 : 1));
            } catch (com.agentsquad.squad.runtime.domain.ExecutionContextInvalidatedException exception) {
                restrictions.restrict(target.userId(), scopeMapper.findScopeByBinding(target.userId(), target.bindingId()), target.bindingId());
            } catch (Exception exception) {
                projectionMapper.defer(target.userId(), target.bindingId(), now().plusSeconds(10));
                log.warn("Agent执行事件同步失败，稍后重试 bindingId={} taskId={} errorType={}",
                        target.bindingId(), target.taskId(), exception.getClass().getSimpleName(), exception);
            }
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}
