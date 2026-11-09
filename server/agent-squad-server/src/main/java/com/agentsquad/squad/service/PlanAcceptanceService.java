package com.agentsquad.squad.service;

import com.agentsquad.squad.enums.AcceptanceVerificationType;
import com.agentsquad.squad.enums.DeterministicAcceptanceCheck;
import com.agentsquad.squad.enums.WorkerJobStatus;
import com.agentsquad.squad.exceptions.RuntimeBusinessException;
import com.agentsquad.squad.enums.RuntimeExceptionCode;
import com.agentsquad.squad.utils.ContentDigests;
import com.agentsquad.squad.mapper.InteractionMapper;
import com.agentsquad.squad.model.bo.PlanAcceptanceResultBO;
import com.agentsquad.squad.enums.PlanAcceptanceVerdict;
import com.agentsquad.squad.model.po.PlanPO;
import com.agentsquad.squad.model.bo.UserAcceptanceReviewBO;
import com.agentsquad.squad.model.po.WorkerJobPO;
import com.agentsquad.squad.model.bo.AcceptanceReviewBO;
import com.agentsquad.squad.model.bo.AcceptanceReviewRequestBO;
import com.agentsquad.squad.model.bo.PlanAcceptanceCriterionBO;
import com.agentsquad.squad.model.bo.PlanPublishBO;
import com.agentsquad.squad.mapper.PlanMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * 当前计划完成条件检查
 * <p>
 * 核对模型评价的成功工作引用和用户真实答复；缺少证据或没有程序检查器的条件保持未知，不伪造通过。
 * 结论由完成候选事务连同计划版本保存，不单独发布最终答案。
 */
@Service
@Slf4j
public class PlanAcceptanceService {
    @Resource
    private PlanMapper plans;

    @Resource
    private ExecutionScopeService scopes;

    @Resource
    private InteractionMapper interactions;

    @Resource
    private ObjectMapper json;

    public List<PlanAcceptanceResultBO> assess(PlanPO plan, List<WorkerJobPO> jobs, List<AcceptanceReviewBO> reviews) {
        var definition = definition(plan);
        var byId = new HashMap<String, AcceptanceReviewBO>();
        if (reviews != null) {
            if (reviews.size() > 20) {
                throw invalid("检查意见不能超过20项");
            }
            for (var review : reviews) {
                if (review == null || review.criterionId() == null || review.verdict() == null
                        || review.evidenceRefs() == null || review.evidenceRefs().size() > 20
                        || review.evidenceRefs().stream().anyMatch(ref -> ref == null || ref.length() > 80)
                        || byId.put(review.criterionId(), review) != null
                        || definition.acceptanceCriteria().stream().noneMatch(c -> c.criterionId().equals(review.criterionId()))) {
                    throw invalid("检查条件或证据格式无效");
                }
            }
        }
        var results = new ArrayList<PlanAcceptanceResultBO>();
        for (var criterion : definition.acceptanceCriteria()) {
            var review = byId.get(criterion.criterionId());
            var verdict = PlanAcceptanceVerdict.UNKNOWN;
            String verifier = "UNVERIFIED";
            List<String> refs = List.of();
            if (criterion.verificationType() == AcceptanceVerificationType.DETERMINISTIC
                    && criterion.deterministicCheck() == DeterministicAcceptanceCheck.REQUIRED_RESULTS_READY) {
                var requiredNodes = definition.nodes().stream().filter(node -> node.required()).map(node -> node.nodeKey()).toList();
                var requiredJobs = jobs.stream().filter(job -> requiredNodes.contains(job.getNodeKey())).toList();
                boolean passed = !requiredNodes.isEmpty() && requiredJobs.size() == requiredNodes.size()
                        && requiredJobs.stream().allMatch(job -> job.getPlanVersion() == plan.getPlanVersion()
                        && WorkerJobStatus.SUCCEEDED.name().equals(job.getStatus()) && job.getResultJson() != null);
                verdict = passed ? PlanAcceptanceVerdict.PASS : PlanAcceptanceVerdict.FAIL;
                verifier = "PLATFORM:REQUIRED_RESULTS_READY";
                refs = requiredJobs.stream().map(WorkerJobPO::getJobId).toList();
            } else if (review != null && !review.evidenceRefs().isEmpty()) {
                if (criterion.verificationType() == AcceptanceVerificationType.MODEL_REVIEW) {
                    for (String ref : review.evidenceRefs()) {
                        if (jobs.stream().noneMatch(job -> job.getJobId().equals(ref) && job.getPlanVersion() == plan.getPlanVersion()
                                && WorkerJobStatus.SUCCEEDED.name().equals(job.getStatus()) && job.getResultJson() != null)) {
                            throw invalid("模型检查引用的结果不属于当前计划的成功工作");
                        }
                    }
                    verdict = review.verdict();
                    verifier = "TASK_MASTER_MODEL";
                    refs = List.copyOf(review.evidenceRefs());
                } else if (criterion.verificationType() == AcceptanceVerificationType.USER_REVIEW) {
                    if (review.evidenceRefs().size() != 1) {
                        throw invalid("用户确认必须引用唯一的实际答复");
                    }
                    var interaction = interactions.findOwned(plan.getUserId(), review.evidenceRefs().get(0));
                    if (interaction == null || !plan.getTaskId().equals(interaction.getTaskId())
                            || interaction.getPlanVersion() != plan.getPlanVersion()
                            || !reviewHash(plan, criterion).equals(interaction.getRequestHash())
                            || !"RESPONDED".equals(interaction.getStatus()) || interaction.getResponseJson() == null) {
                        throw invalid("用户确认尚未答复或不属于当前条件和计划");
                    }
                    try {
                        var accepted = json.readTree(interaction.getResponseJson()).path("value").path("accepted");
                        if (accepted.isBoolean()) {
                            verdict = accepted.asBoolean() ? PlanAcceptanceVerdict.PASS : PlanAcceptanceVerdict.FAIL;
                            verifier = "USER:" + plan.getUserId();
                            refs = List.of(interaction.getInteractionId());
                        }
                    } catch (java.io.IOException exception) {
                        throw new IllegalStateException("用户验收答复无法解析", exception);
                    }
                }
            }
            results.add(new PlanAcceptanceResultBO(criterion.criterionId(), criterion.text(), criterion.required(), criterion.verificationType(),
                    verdict, verifier, refs, plan.getPlanVersion(), plan.getGoalRevision()));
        }
        return List.copyOf(results);
    }

    /**
     * 仅当前目标 Master 可以请求验收，问题按不可变计划生成；实际挂起仍使用 SDK 公开机制。
     */
    @Transactional
    public UserAcceptanceReviewBO prepareUserReview(String userId, String sessionId, AcceptanceReviewRequestBO request) {
        scopes.lockOwned(userId, scopes.scopeForSession(userId, sessionId), true);
        var context = plans.lockMasterContext(userId, sessionId);
        if (context == null || context.isCancellationRequested() || request == null
                || context.getPlanVersion() != request.expectedPlanVersion()
                || !("RUNNING".equals(context.getTaskStatus()) || "QUEUED".equals(context.getTaskStatus()))) {
            throw invalid("只能确认当前执行中的计划");
        }
        var plan = plans.lockPlan(userId, context.getTaskId(), context.getPlanVersion());
        if (plan == null || !"ACTIVE".equals(plan.getStatus())
                || plans.countActiveJobs(userId, context.getTaskId(), context.getPlanVersion()) > 0) {
            throw invalid("工作尚未收尾，暂时不能请求最终确认");
        }
        var criterion = definition(plan).acceptanceCriteria().stream()
                .filter(item -> item.criterionId().equals(request.criterionId())
                        && item.verificationType() == AcceptanceVerificationType.USER_REVIEW).findFirst()
                .orElseThrow(() -> invalid("当前计划没有这项用户确认条件"));
        log.debug("用户验收条件已核对 taskId={} planVersion={} criterionId={}",
                plan.getTaskId(), plan.getPlanVersion(), criterion.criterionId());
        return new UserAcceptanceReviewBO(criterion.criterionId(), plan.getPlanVersion(),
                "请确认这项结果是否符合要求：" + criterion.text(), reviewHash(plan, criterion));
    }

    public PlanPublishBO definition(PlanPO plan) {
        try {
            return json.readValue(plan.getDefinitionJson(), PlanPublishBO.class);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("当前计划完成条件无法解析", exception);
        }
    }

    private String reviewHash(PlanPO plan, PlanAcceptanceCriterionBO criterion) {
        return ContentDigests.sha256(plan.getTaskId() + ":" + plan.getPlanVersion() + ":" + plan.getGoalRevision()
                + ":" + criterion.criterionId() + ":" + criterion.text());
    }

    private RuntimeBusinessException invalid(String message) {
        return new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION, message);
    }
}
