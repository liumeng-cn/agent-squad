package com.agentsquad.squad.runtime.service;

import com.agentsquad.squad.common.exception.RuntimeBusinessException;
import com.agentsquad.squad.common.exception.RuntimeExceptionCode;

import com.agentsquad.squad.runtime.domain.CompletionContextRow;
import com.agentsquad.squad.conversation.domain.ExecutionScopeRow;
import com.agentsquad.squad.conversation.repository.ExecutionScopeMapper;
import com.agentsquad.squad.conversation.service.ExecutionScopeService;
import com.agentsquad.squad.conversation.service.ResponseExecutionService;
import com.agentsquad.squad.conversation.domain.GoalTaskStatus;
import com.agentsquad.squad.presentation.dto.CompletionProposalReceipt;
import com.agentsquad.squad.runtime.repository.CompletionMapper;
import com.agentsquad.squad.runtime.repository.PlanMapper;
import com.agentsquad.squad.common.util.ContentDigests;
import com.agentsquad.squad.common.util.RuntimePayloads;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Master 完成候选服务。只保存候选，不修改任务终态或释放对话占用。
 *
 * <p>用户、原生会话和 Turn 来自框架运行上下文，任务归属由数据库绑定解析；模型不能指定目标 ID。</p>
 */
@Service
@Slf4j
@ConditionalOnProperty(prefix = "agentsquad.delivery", name = "enabled", havingValue = "true")
public class CompletionProposalService {

    private static final Set<String> ALLOWED_OUTCOMES = Set.of(
            GoalTaskStatus.SUCCEEDED.name(), GoalTaskStatus.PARTIAL.name());

    @Resource
    private ExecutionScopeMapper scopeMapper;

    @Resource
    private ExecutionScopeService scopes;

    @Resource
    private ResponseExecutionService responses;

    @Resource
    private CompletionMapper completionMapper;

    @Resource
    private PlanMapper planMapper;

    @Resource
    private ObjectMapper objectMapper;

    /**
     * 同一原生 Turn 的相同候选返回原回执；不同内容或不同 Turn 的重复提交拒绝。
     * 事务提交后即使工具回包丢失，重放也不会生成第二个候选。
     */
    @Transactional
    public CompletionProposalReceipt propose(String userId, String sessionId, String turnId,
                                             String toolCallId, String outcome, String finalAnswer) {
        if (isBlank(userId) || isBlank(sessionId) || isBlank(turnId) || isBlank(toolCallId)
                || turnId.length() > 64 || toolCallId.length() > 128) {
            throw new IllegalStateException("完成候选缺少可信运行身份");
        }
        if (!ALLOWED_OUTCOMES.contains(outcome) || isBlank(finalAnswer)
                || finalAnswer.length() > 200000) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION, "完成候选内容无效");
        }

        ExecutionScopeRow scope = scopes.lockOwned(userId, scopes.scopeForSession(userId, sessionId), true);
        if ("RESPONSE".equals(scope.kind())) {
            return responses.propose(userId, scopeMapper.findBindingBySession(userId, sessionId), turnId, toolCallId, outcome, finalAnswer);
        }
        CompletionContextRow context = completionMapper.lockByMasterSession(userId, sessionId);
        if (context == null) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.NOT_FOUND, "Master 会话未绑定目标");
        }
        if (context.cancellationRequested()
                || !(GoalTaskStatus.QUEUED.name().equals(context.taskStatus())
                || GoalTaskStatus.RUNNING.name().equals(context.taskStatus()))) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "目标已不能提交完成候选");
        }
        if (context.planVersion() > 0
                && (planMapper.countActiveJobs(userId, context.taskId(), context.planVersion()) > 0
                || (GoalTaskStatus.SUCCEEDED.name().equals(outcome)
                && planMapper.countUnfinishedJobs(userId, context.taskId(), context.planVersion()) > 0))) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT,
                    "Worker 尚未全部完成，当前不能提交最终回答");
        }

        if (completionMapper.countPendingEffects(userId, context.taskId()) > 0) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "仍有待处理交互或未知操作，不能结束目标");
        }
        Map<String, Object> proposal = new LinkedHashMap<>();
        proposal.put("outcome", outcome);
        proposal.put("finalAnswer", finalAnswer);
        proposal.put("evidenceRefs", List.of());
        proposal.put("planVersion", context.planVersion());
        proposal.put("answerRevision", context.answerRevision());
        proposal.put("sourceBindingId", context.bindingId());
        proposal.put("sourceTurnId", turnId);
        String proposalJson = RuntimePayloads.toJson(objectMapper, proposal);
        String proposalHash = ContentDigests.sha256(proposalJson);
        if (context.proposalJson() != null) {
            if (turnId.equals(context.proposalTurnId()) && proposalHash.equals(context.proposalHash())) {
                return new CompletionProposalReceipt(context.taskId(), context.bindingId(), turnId,
                        context.proposalToolCallId(), "RECORDED");
            }
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "目标已有不同的完成候选");
        }
        if (completionMapper.saveProposal(userId, context.taskId(), context.bindingId(), turnId,
                toolCallId, proposalHash, proposalJson) != 1) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "完成候选提交时目标状态已变化");
        }
        log.info("Master 完成候选已持久化 taskId={} bindingId={} turnId={} outcome={} answerLength={}",
                context.taskId(), context.bindingId(), turnId, outcome, finalAnswer.length());
        return new CompletionProposalReceipt(context.taskId(), context.bindingId(), turnId,
                toolCallId, "RECORDED");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

}
