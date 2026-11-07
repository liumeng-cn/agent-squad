package com.agentsquad.squad.runtime.service;

import com.agentsquad.squad.common.enums.worker.AcceptanceVerificationType;
import com.agentsquad.squad.common.enums.worker.WorkerJobStatus;
import com.agentsquad.squad.common.exception.RuntimeBusinessException;
import com.agentsquad.squad.common.exception.RuntimeExceptionCode;
import com.agentsquad.squad.common.util.ContentDigests;
import com.agentsquad.squad.conversation.service.ExecutionScopeService;
import com.agentsquad.squad.interaction.repository.InteractionMapper;
import com.agentsquad.squad.domain.model.PlanAcceptanceResult;
import com.agentsquad.squad.common.enums.worker.PlanAcceptanceVerdict;
import com.agentsquad.squad.domain.entity.PlanDO;
import com.agentsquad.squad.domain.model.UserAcceptanceReview;
import com.agentsquad.squad.domain.entity.WorkerJobDO;
import com.agentsquad.squad.domain.dto.AcceptanceReview;
import com.agentsquad.squad.domain.dto.AcceptanceReviewRequest;
import com.agentsquad.squad.domain.dto.PlanAcceptanceCriterion;
import com.agentsquad.squad.domain.dto.PlanPublish;
import com.agentsquad.squad.runtime.repository.PlanMapper;
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

    public List<PlanAcceptanceResult> assess(PlanDO plan, List<WorkerJobDO> jobs, List<AcceptanceReview> reviews) {
        var definition = definition(plan);
        var byId = new HashMap<String, AcceptanceReview>();
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
        var results = new ArrayList<PlanAcceptanceResult>();
        for (var criterion : definition.acceptanceCriteria()) {
            var review = byId.get(criterion.criterionId());
            var verdict = PlanAcceptanceVerdict.UNKNOWN;
            String verifier = "UNVERIFIED";
            List<String> refs = List.of();
            if (criterion.verificationType() == AcceptanceVerificationType.DETERMINISTIC
                    && criterion.deterministicCheck() == com.agentsquad.squad.common.enums.worker.DeterministicAcceptanceCheck.REQUIRED_RESULTS_READY) {
                var requiredNodes = definition.nodes().stream().filter(node -> node.required()).map(node -> node.nodeKey()).toList();
                var requiredJobs = jobs.stream().filter(job -> requiredNodes.contains(job.nodeKey())).toList();
                boolean passed = !requiredNodes.isEmpty() && requiredJobs.size() == requiredNodes.size()
                        && requiredJobs.stream().allMatch(job -> job.planVersion() == plan.planVersion()
                        && WorkerJobStatus.SUCCEEDED.name().equals(job.status()) && job.resultJson() != null);
                verdict = passed ? PlanAcceptanceVerdict.PASS : PlanAcceptanceVerdict.FAIL;
                verifier = "PLATFORM:REQUIRED_RESULTS_READY";
                refs = requiredJobs.stream().map(WorkerJobDO::jobId).toList();
            } else if (review != null && !review.evidenceRefs().isEmpty()) {
                if (criterion.verificationType() == AcceptanceVerificationType.MODEL_REVIEW) {
                    for (String ref : review.evidenceRefs()) {
                        if (jobs.stream().noneMatch(job -> job.jobId().equals(ref) && job.planVersion() == plan.planVersion()
                                && WorkerJobStatus.SUCCEEDED.name().equals(job.status()) && job.resultJson() != null)) {
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
                    var interaction = interactions.findOwned(plan.userId(), review.evidenceRefs().get(0));
                    if (interaction == null || !plan.taskId().equals(interaction.taskId())
                            || interaction.planVersion() != plan.planVersion()
                            || !reviewHash(plan, criterion).equals(interaction.requestHash())
                            || !"RESPONDED".equals(interaction.status()) || interaction.responseJson() == null) {
                        throw invalid("用户确认尚未答复或不属于当前条件和计划");
                    }
                    try {
                        var accepted = json.readTree(interaction.responseJson()).path("value").path("accepted");
                        if (accepted.isBoolean()) {
                            verdict = accepted.asBoolean() ? PlanAcceptanceVerdict.PASS : PlanAcceptanceVerdict.FAIL;
                            verifier = "USER:" + plan.userId();
                            refs = List.of(interaction.interactionId());
                        }
                    } catch (java.io.IOException exception) {
                        throw new IllegalStateException("用户验收答复无法解析", exception);
                    }
                }
            }
            results.add(new PlanAcceptanceResult(criterion.criterionId(), criterion.text(), criterion.required(), criterion.verificationType(),
                    verdict, verifier, refs, plan.planVersion(), plan.goalRevision()));
        }
        return List.copyOf(results);
    }

    /**
     * 仅当前目标 Master 可以请求验收，问题按不可变计划生成；实际挂起仍使用 SDK 公开机制。
     */
    @Transactional
    public UserAcceptanceReview prepareUserReview(String userId, String sessionId, AcceptanceReviewRequest request) {
        scopes.lockOwned(userId, scopes.scopeForSession(userId, sessionId), true);
        var context = plans.lockMasterContext(userId, sessionId);
        if (context == null || context.cancellationRequested() || request == null
                || context.planVersion() != request.expectedPlanVersion()
                || !("RUNNING".equals(context.taskStatus()) || "QUEUED".equals(context.taskStatus()))) {
            throw invalid("只能确认当前执行中的计划");
        }
        var plan = plans.lockPlan(userId, context.taskId(), context.planVersion());
        if (plan == null || !"ACTIVE".equals(plan.status())
                || plans.countActiveJobs(userId, context.taskId(), context.planVersion()) > 0) {
            throw invalid("工作尚未收尾，暂时不能请求最终确认");
        }
        var criterion = definition(plan).acceptanceCriteria().stream()
                .filter(item -> item.criterionId().equals(request.criterionId())
                        && item.verificationType() == AcceptanceVerificationType.USER_REVIEW).findFirst()
                .orElseThrow(() -> invalid("当前计划没有这项用户确认条件"));
        log.debug("用户验收条件已核对 taskId={} planVersion={} criterionId={}",
                plan.taskId(), plan.planVersion(), criterion.criterionId());
        return new UserAcceptanceReview(criterion.criterionId(), plan.planVersion(),
                "请确认这项结果是否符合要求：" + criterion.text(), reviewHash(plan, criterion));
    }

    public PlanPublish definition(PlanDO plan) {
        try {
            return json.readValue(plan.definitionJson(), PlanPublish.class);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("当前计划完成条件无法解析", exception);
        }
    }

    private String reviewHash(PlanDO plan, PlanAcceptanceCriterion criterion) {
        return ContentDigests.sha256(plan.taskId() + ":" + plan.planVersion() + ":" + plan.goalRevision()
                + ":" + criterion.criterionId() + ":" + criterion.text());
    }

    private RuntimeBusinessException invalid(String message) {
        return new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION, message);
    }
}
