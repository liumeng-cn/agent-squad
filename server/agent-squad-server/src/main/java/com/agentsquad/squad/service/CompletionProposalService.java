package com.agentsquad.squad.service;

import com.agentsquad.squad.model.bo.CompletionContextBO;
import com.agentsquad.squad.model.po.ExecutionScopePO;
import com.agentsquad.squad.enums.GoalTaskStatus;
import com.agentsquad.squad.enums.PlanJoinStage;
import com.agentsquad.squad.model.bo.PlanAcceptanceResultBO;

import com.agentsquad.squad.mapper.TaskDirectiveMapper;
import com.agentsquad.squad.exceptions.RuntimeBusinessException;
import com.agentsquad.squad.enums.RuntimeExceptionCode;

import com.agentsquad.squad.model.bo.CompletionSubmissionBO;
import com.agentsquad.squad.mapper.ExecutionScopeMapper;
import com.agentsquad.squad.model.bo.CompletionProposalReceiptBO;
import com.agentsquad.squad.mapper.CompletionMapper;
import com.agentsquad.squad.mapper.PlanMapper;
import com.agentsquad.squad.utils.ContentDigests;
import com.agentsquad.squad.utils.RuntimePayloads;
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
    private TaskDirectiveMapper directives;

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

    @Resource
    private PlanCompletionEvaluationService planEvaluation;

    /**
     * 同一原生 Turn 的相同候选返回原回执；不同内容或不同 Turn 的重复提交拒绝。
     * 事务提交后即使工具回包丢失，重放也不会生成第二个候选。
     */
    @Transactional
    public CompletionProposalReceiptBO propose(String userId, String sessionId, String turnId,
                                             String toolCallId, String outcome, String finalAnswer) {
        return submit(new CompletionSubmissionBO(userId, sessionId, turnId,
                toolCallId, outcome, finalAnswer, List.of()));
    }

    /** 保存当前计划逐项检查结果；没有证据的成功声明被拒绝，发布前还需再次核对。 */
    @Transactional
    public CompletionProposalReceiptBO submit(CompletionSubmissionBO submission) {
        String userId = submission.userId();
        String sessionId = submission.sessionId();
        String turnId = submission.turnId();
        String toolCallId = submission.toolCallId();
        String outcome = submission.outcome();
        String finalAnswer = submission.finalAnswer();
        if (isBlank(userId) || isBlank(sessionId) || isBlank(turnId) || isBlank(toolCallId)
                || turnId.length() > 64 || toolCallId.length() > 128) {
            throw new IllegalStateException("完成候选缺少可信运行身份");
        }
        if (!ALLOWED_OUTCOMES.contains(outcome) || isBlank(finalAnswer)
                || finalAnswer.length() > 200000) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION, "完成候选内容无效");
        }

        ExecutionScopePO scope = scopes.lockOwned(userId, scopes.scopeForSession(userId, sessionId), true);
        if ("RESPONSE".equals(scope.getKind())) {
            return responses.propose(userId, scopeMapper.findBindingBySession(userId, sessionId), turnId, toolCallId, outcome, finalAnswer);
        }
        if (!directives.pending(userId, scope.getOwnerId()).isEmpty()) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "尚有用户要求待协调处理，请结束当前回合等待修改通知");
        }
        CompletionContextBO context = completionMapper.lockByMasterSession(userId, sessionId);
        if (context == null) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.NOT_FOUND, "Master 会话未绑定目标");
        }
        if (context.isCancellationRequested()
                || !(GoalTaskStatus.QUEUED.name().equals(context.getTaskStatus())
                || GoalTaskStatus.RUNNING.name().equals(context.getTaskStatus()))) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "目标已不能提交完成候选");
        }
        var acceptanceResults = List.<PlanAcceptanceResultBO>of();
        if (context.getPlanVersion() > 0) {
            var evaluation = planEvaluation.evaluate(userId, context.getTaskId(), context.getPlanVersion(), submission.reviews());
            if (evaluation.decision().stage() != PlanJoinStage.READY
                    || (GoalTaskStatus.SUCCEEDED.name().equals(outcome)
                    && evaluation.decision().outcome() != GoalTaskStatus.SUCCEEDED)) {
                throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT,
                        "工作尚未收尾或必要条件没有通过检查，请补充逐项证据，不能直接声明全部完成");
            }
            if (evaluation.decision().outcome() == GoalTaskStatus.FAILED) {
                outcome = GoalTaskStatus.FAILED.name();
            }
            acceptanceResults = evaluation.acceptanceResults();
        }

        if (completionMapper.countPendingEffects(userId, context.getTaskId()) > 0) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "仍有待处理交互或未知操作，不能结束目标");
        }
        Map<String, Object> proposal = new LinkedHashMap<>();
        proposal.put("outcome", outcome);
        proposal.put("finalAnswer", finalAnswer);
        proposal.put("evidenceRefs", List.of());
        proposal.put("planVersion", context.getPlanVersion());
        proposal.put("answerRevision", context.getAnswerRevision());
        proposal.put("sourceBindingId", context.getBindingId());
        proposal.put("sourceTurnId", turnId);
        proposal.put("acceptanceReviews", submission.reviews() == null ? List.of() : submission.reviews());
        proposal.put("acceptanceResults", acceptanceResults);
        String proposalJson = RuntimePayloads.toJson(objectMapper, proposal);
        String proposalHash = ContentDigests.sha256(proposalJson);
        if (context.getProposalJson() != null) {
            if (turnId.equals(context.getProposalTurnId()) && proposalHash.equals(context.getProposalHash())) {
                return new CompletionProposalReceiptBO(context.getTaskId(), context.getBindingId(), turnId,
                        context.getProposalToolCallId(), "RECORDED");
            }
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "目标已有不同的完成候选");
        }
        if (completionMapper.saveProposal(userId, context.getTaskId(), context.getBindingId(), turnId,
                toolCallId, proposalHash, proposalJson) != 1) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "完成候选提交时目标状态已变化");
        }
        log.info("最终回答及检查结果已保存 taskId={} bindingId={} turnId={} outcome={} answerLength={} acceptanceChecks={}",
                context.getTaskId(), context.getBindingId(), turnId, outcome, finalAnswer.length(), acceptanceResults.size());
        return new CompletionProposalReceiptBO(context.getTaskId(), context.getBindingId(), turnId,
                toolCallId, "RECORDED");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

}
