package com.agentsquad.squad.integration.agentscope;

import com.agentsquad.squad.runtime.domain.ProjectionTarget;
import com.agentsquad.squad.runtime.repository.ProjectionMapper;
import com.agentsquad.squad.runtime.domain.MasterAgentIdentity;
import com.agentsquad.squad.runtime.service.WorkerRecoveryService;
import com.agentsquad.squad.integration.agentscope.inbox.RuntimeEventConsumptionService;
import com.agentsquad.squad.integration.agentscope.inbox.RuntimeEventReceiptService;
import com.agentsquad.squad.integration.agentscope.inbox.RuntimeEventSource;
import com.agentsquad.squad.integration.agentscope.inbox.RuntimeEventFailureService;
import com.agentsquad.squad.integration.agentscope.inbox.RuntimeEventFailureCode;
import io.agentscope.core.session.SessionEvent;
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
    private RuntimeEventReceiptService receipts;

    @Resource
    private RuntimeEventConsumptionService consumption;

    @Resource
    private RuntimeEventFailureService consumptionFailures;

    @Resource
    private WorkerRecoveryService workerRecoveryService;

    /**
     * 每个 Agent 会话各自处理失败和重试，不影响其他会话。
     */
    @Scheduled(fixedDelayString = "${agentsquad.projection.poll-delay-ms:1000}")
    public void projectDue() {
        for (ProjectionTarget target : projectionMapper.findDueTargets(now())) {
            List<SessionEvent> events = List.of();
            try {
                authorization.requireBindingCurrent(target.userId(), target.bindingId());
                // 恢复属于后台执行步骤，不能藏在读取日志或检查收据的方法里。
                nativeSessionGateway.startPending(target);
                events = nativeSessionGateway.readCommittedEvents(target, target.cursor(), 100);
                var source = new RuntimeEventSource(target.userId(), target.bindingId(), target.sessionId(),
                        RuntimeEventSource.CURRENT_MAPPING_VERSION);
                for (SessionEvent event : events) {
                    receipts.receive(source, event);
                }
                consumption.consume(target, events);
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
                recordConsumptionFailure(target, events, exception);
                projectionMapper.defer(target.userId(), target.bindingId(), now().plusSeconds(10));
                log.warn("Agent执行事件同步失败，稍后重试 bindingId={} taskId={} errorType={}",
                        target.bindingId(), target.taskId(), exception.getClass().getSimpleName(), exception);
            }
        }
    }

    private void recordConsumptionFailure(ProjectionTarget target, List<SessionEvent> events, Exception failure) {
        if (events.isEmpty()) {
            return;
        }
        long sequence = events.getFirst().seq();
        RuntimeEventFailureCode code = RuntimeEventFailureCode.BUSINESS_RETRY;
        if (failure instanceof RuntimeEventConsumptionException consumptionFailure) {
            sequence = consumptionFailure.sequence();
            code = consumptionFailure.code();
        }
        try {
            consumptionFailures.record(new RuntimeEventSource(target.userId(), target.bindingId(), target.sessionId(),
                    RuntimeEventSource.CURRENT_MAPPING_VERSION), sequence, code);
        } catch (Exception persistenceFailure) {
            log.warn("原生事件失败记录暂未保存 bindingId={} sourceSeq={} errorType={}", target.bindingId(), sequence,
                    persistenceFailure.getClass().getSimpleName(), persistenceFailure);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}
