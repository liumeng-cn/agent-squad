package com.agentsquad.squad.service;

import com.agentsquad.squad.agent.session.NativeSessionGateway;
import com.agentsquad.squad.model.bo.NativeSubmissionReceiptBO;

import com.agentsquad.squad.exceptions.ExecutionContextInvalidatedException;
import com.agentsquad.squad.enums.ResponseCompletionProtocol;
import com.agentsquad.squad.model.bo.ProjectionTargetBO;
import com.agentsquad.squad.mapper.ProjectionMapper;
import com.agentsquad.squad.agent.MasterAgentIdentity;
import com.agentsquad.squad.model.bo.RuntimeEventSourceBO;
import com.agentsquad.squad.enums.RuntimeEventFailureCode;
import io.agentscope.core.session.SessionEvent;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import com.agentsquad.squad.exceptions.RuntimeEventConsumptionException;

/**
 * 从上次处理到的事件序号继续读取 AgentScope 执行事件，并依次更新业务状态。
 */
@Component
@Slf4j
@ConditionalOnProperty(prefix = "agentsquad.delivery", name = "enabled", havingValue = "true")
public class NativeProjectionPoller {
    @Resource
    private com.agentsquad.squad.observer.ExecutionTrace executionTrace;

    @Resource
    private com.agentsquad.squad.service.ExecutionAuthorizationService authorization;

    @Resource
    private com.agentsquad.squad.service.ExecutionRestrictionService restrictions;

    @Resource
    private com.agentsquad.squad.mapper.ExecutionScopeMapper scopeMapper;

    @Resource
    private com.agentsquad.squad.service.ExecutionFailureService failures;

    @Resource
    private NaturalReplyOutputReader naturalOutputs;

    @Resource
    private com.agentsquad.squad.mapper.ResponseRuntimeMapper replies;

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
        for (ProjectionTargetBO target : projectionMapper.findDueTargets(now())) {
            List<SessionEvent> events = List.of();
            try {
                authorization.requireBindingCurrent(target.getUserId(), target.getBindingId());
                // 恢复属于后台执行步骤，不能藏在读取日志或检查收据的方法里。
                nativeSessionGateway.startPending(target);
                events = nativeSessionGateway.readCommittedEvents(target, target.getCursor(), 100);
                var source = new RuntimeEventSourceBO(target.getUserId(), target.getBindingId(), target.getSessionId(),
                        RuntimeEventSourceBO.CURRENT_MAPPING_VERSION);
                for (SessionEvent event : events) {
                    receipts.receive(source, event);
                }
                if (!events.isEmpty()) {
                    var batch = events;
                    executionTrace.project(target.getUserId(), target.getBindingId(), () -> consumption.consume(target, batch));
                }
                if (target.getTaskId() == null) {
                    String commandId = responses.originalCommand(target);
                    if (commandId != null) {
                        NativeSubmissionReceiptBO receipt = nativeSessionGateway.inspectTask(target, commandId);
                        if (commandId.equals(receipt.commandId())) {
                            var evidence = "completed".equals(receipt.status())
                                    && replies.completionProtocol(target.getUserId(), target.getBindingId())
                                    == ResponseCompletionProtocol.NATURAL
                                    ? naturalOutputs.readCompleted(target, receipt) : null;
                            responses.recover(target, receipt, evidence);
                        }
                    }
                } else if (!MasterAgentIdentity.AGENT_ID.equals(target.getAgentId())) {
                    String commandId = workerRecoveryService.originalSubmitCommandId(target);
                    if (commandId != null) {
                        NativeSubmissionReceiptBO receipt = nativeSessionGateway.inspectTask(target, commandId);
                        if (commandId.equals(receipt.commandId())) {
                            workerRecoveryService.queueInterrupted(target, receipt);
                        }
                    }
                } else {
                    String commandId = failures.masterCommand(target);
                    if (commandId != null) {
                        NativeSubmissionReceiptBO receipt = nativeSessionGateway.inspectTask(target, commandId);
                        if (commandId.equals(receipt.commandId())) { failures.recoverInterrupted(target, receipt); }
                    }
                }
                projectionMapper.defer(target.getUserId(), target.getBindingId(),
                        now().plusSeconds(events.isEmpty() ? 5 : 1));
            } catch (ExecutionContextInvalidatedException exception) {
                restrictions.restrict(target.getUserId(), scopeMapper.findScopeByBinding(target.getUserId(), target.getBindingId()), target.getBindingId());
            } catch (Exception exception) {
                recordConsumptionFailure(target, events, exception);
                projectionMapper.defer(target.getUserId(), target.getBindingId(), now().plusSeconds(10));
                log.warn("Agent执行事件同步失败，稍后重试 bindingId={} taskId={} errorType={}",
                        target.getBindingId(), target.getTaskId(), exception.getClass().getSimpleName(), exception);
            }
        }
    }

    private void recordConsumptionFailure(ProjectionTargetBO target, List<SessionEvent> events, Exception failure) {
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
            consumptionFailures.record(new RuntimeEventSourceBO(target.getUserId(), target.getBindingId(), target.getSessionId(),
                    RuntimeEventSourceBO.CURRENT_MAPPING_VERSION), sequence, code);
        } catch (Exception persistenceFailure) {
            log.warn("原生事件失败记录暂未保存 bindingId={} sourceSeq={} errorType={}", target.getBindingId(), sequence,
                    persistenceFailure.getClass().getSimpleName(), persistenceFailure);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}
