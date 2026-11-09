package com.agentsquad.squad.service;

import com.agentsquad.squad.bo.PlanJoinJobBO;

import com.agentsquad.squad.bo.PlanJoinFactsBO;

import com.agentsquad.squad.bo.PlanJoinDecisionBO;

import com.agentsquad.squad.enums.GoalTaskStatus;
import com.agentsquad.squad.enums.PlanJoinStage;

import com.agentsquad.squad.enums.PlanAcceptanceVerdict;

import com.agentsquad.squad.enums.WorkerJobStatus;

import java.util.HashSet;
import java.util.Objects;

/**
 * 计划汇总与完成规则
 * <p>
 * 纯计算当前计划的执行、恢复和验收结论，不读数据库、不调用模型，也不直接发布答案。
 */
public final class PlanJoinPolicyService {

    /**
     * 全部已接受工作先明确收尾；必要失败或验收未知禁止成功，有已保存结果才允许部分交付。
     * 两种汇总策略均遵守这一底线，不因 BEST_EFFORT 提前遗弃在途分支。
     */
    public PlanJoinDecisionBO evaluate(PlanJoinFactsBO facts) {
        validate(facts);
        boolean active = false;
        boolean coordinate = false;
        boolean requiredSucceeded = true;
        for (var job : facts.jobs()) {
            switch (job.status()) {
                case READY, RUNNING, WAITING_INPUT, WAITING_APPROVAL, RECOVERING, RECONCILING, CANCELLING ->
                        active = true;
                case BLOCKED, RECOVERY_BLOCKED -> coordinate = true;
                case SUCCEEDED, PARTIAL, FAILED, CANCELLED, SKIPPED, SUPERSEDED -> {
                }
            }
            if (job.required() && job.status() != WorkerJobStatus.SUCCEEDED) {
                requiredSucceeded = false;
            }
        }
        if (active) {
            return new PlanJoinDecisionBO(PlanJoinStage.WAITING_WORK, null);
        }
        if (facts.pendingEffects()) {
            return new PlanJoinDecisionBO(PlanJoinStage.WAITING_EFFECTS, null);
        }
        if (coordinate) {
            return new PlanJoinDecisionBO(PlanJoinStage.NEEDS_COORDINATION, null);
        }
        boolean accepted = !facts.requiredAcceptance().isEmpty()
                && facts.requiredAcceptance().stream().allMatch(verdict -> verdict == PlanAcceptanceVerdict.PASS);
        GoalTaskStatus outcome = requiredSucceeded && accepted && facts.hasDeliverable()
                ? GoalTaskStatus.SUCCEEDED
                : (facts.hasDeliverable() ? GoalTaskStatus.PARTIAL : GoalTaskStatus.FAILED);
        return new PlanJoinDecisionBO(PlanJoinStage.READY, outcome);
    }

    private void validate(PlanJoinFactsBO facts) {
        Objects.requireNonNull(facts, "缺少计划事实");
        Objects.requireNonNull(facts.policy(), "缺少汇总策略");
        Objects.requireNonNull(facts.jobs(), "缺少计划工作");
        Objects.requireNonNull(facts.requiredAcceptance(), "缺少验收结论");
        if (facts.planVersion() < 1 || facts.jobs().isEmpty() || facts.jobs().stream().noneMatch(PlanJoinJobBO::required)) {
            throw new IllegalArgumentException("计划版本无效或没有必要工作");
        }
        var ids = new HashSet<String>();
        for (var job : facts.jobs()) {
            if (job.jobId() == null || job.jobId().isBlank() || !ids.add(job.jobId())
                    || job.planVersion() != facts.planVersion() || job.status() == null) {
                throw new IllegalArgumentException("工作身份重复、状态无效或不属于当前计划");
            }
        }
        if (facts.requiredAcceptance().stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("验收结论无效");
        }
    }
}
