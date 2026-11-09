package com.agentsquad.squad.service;

import com.agentsquad.squad.enums.WorkerJobStatus;
import com.agentsquad.squad.exceptions.RuntimeBusinessException;
import com.agentsquad.squad.enums.RuntimeExceptionCode;
import com.agentsquad.squad.model.bo.PlanCompletionEvaluationBO;
import com.agentsquad.squad.model.bo.PlanJoinFactsBO;
import com.agentsquad.squad.model.bo.PlanJoinJobBO;
import com.agentsquad.squad.model.bo.AcceptanceReviewBO;
import com.agentsquad.squad.mapper.CompletionMapper;
import com.agentsquad.squad.mapper.PlanMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 当前计划完成检查的事务装配
 * <p>
 * 调用方持有目标锁；统一读取当前计划、工作结果和外部效果，再调用纯规则，提交和发布共用同一入口。
 */
@Service
public class PlanCompletionEvaluationService {
    @Resource
    private PlanMapper plans;

    @Resource
    private CompletionMapper completions;

    @Resource
    private PlanAcceptanceService acceptance;

    public PlanCompletionEvaluationBO evaluate(String userId, String taskId, long planVersion, List<AcceptanceReviewBO> reviews) {
        var plan = plans.lockPlan(userId, taskId, planVersion);
        if (plan == null || !"ACTIVE".equals(plan.getStatus())) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "当前计划已经变化");
        }
        var jobs = plans.lockJobs(taskId, planVersion);
        var definition = acceptance.definition(plan);
        var required = definition.nodes().stream().filter(node -> node.required()).map(node -> node.nodeKey()).toList();
        var facts = jobs.stream().map(job -> new PlanJoinJobBO(job.getJobId(), job.getPlanVersion(), required.contains(job.getNodeKey()),
                WorkerJobStatus.valueOf(job.getStatus()))).toList();
        var results = acceptance.assess(plan, jobs, reviews);
        var decision = new PlanJoinPolicyService().evaluate(new PlanJoinFactsBO(planVersion,
                definition.joinPolicy(), facts, completions.countPendingEffects(userId, taskId) > 0,
                results.stream().filter(result -> result.required()).map(result -> result.verdict()).toList(),
                jobs.stream().anyMatch(job -> "SUCCEEDED".equals(job.getStatus()) && job.getResultJson() != null)));
        return new PlanCompletionEvaluationBO(decision, results);
    }
}
