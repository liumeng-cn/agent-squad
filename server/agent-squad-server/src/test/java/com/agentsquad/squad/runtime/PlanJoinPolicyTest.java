package com.agentsquad.squad.runtime;

import com.agentsquad.squad.enums.PlanJoinPolicy;
import com.agentsquad.squad.enums.WorkerJobStatus;
import com.agentsquad.squad.enums.GoalTaskStatus;
import com.agentsquad.squad.enums.PlanAcceptanceVerdict;
import com.agentsquad.squad.model.bo.PlanJoinFactsBO;
import com.agentsquad.squad.model.bo.PlanJoinJobBO;
import com.agentsquad.squad.service.PlanJoinPolicyService;
import com.agentsquad.squad.enums.PlanJoinStage;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 计划完成规则验证
 * <p>
 * 两种策略均验证必要工作、可选在途、未知效果及验收证据，纯计算不启动模型和数据库。
 */
class PlanJoinPolicyTest {
    private final PlanJoinPolicyService policy = new PlanJoinPolicyService();

    @ParameterizedTest
    @EnumSource(PlanJoinPolicy.class)
    void waitsForEveryAcceptedBranch(PlanJoinPolicy strategy) {
        for (var status : List.of(WorkerJobStatus.READY, WorkerJobStatus.RUNNING, WorkerJobStatus.WAITING_INPUT,
                WorkerJobStatus.WAITING_APPROVAL, WorkerJobStatus.RECOVERING, WorkerJobStatus.RECONCILING,
                WorkerJobStatus.CANCELLING)) {
            var decision = policy.evaluate(facts(strategy, List.of(job("required", true, WorkerJobStatus.SUCCEEDED),
                    job("optional", false, status)), false, List.of(PlanAcceptanceVerdict.PASS), true));
            assertThat(decision.stage()).isEqualTo(PlanJoinStage.WAITING_WORK);
            assertThat(decision.outcome()).isNull();
        }
    }

    @ParameterizedTest
    @EnumSource(PlanJoinPolicy.class)
    void doesNotConfuseCoordinationWithCompletion(PlanJoinPolicy strategy) {
        for (var status : List.of(WorkerJobStatus.BLOCKED, WorkerJobStatus.RECOVERY_BLOCKED)) {
            var decision = policy.evaluate(facts(strategy, List.of(job("work", true, status)), false,
                    List.of(PlanAcceptanceVerdict.PASS), true));
            assertThat(decision.stage()).isEqualTo(PlanJoinStage.NEEDS_COORDINATION);
            assertThat(decision.outcome()).isNull();
        }
    }

    @ParameterizedTest
    @EnumSource(PlanJoinPolicy.class)
    void unknownEffectsBlockPublication(PlanJoinPolicy strategy) {
        var decision = policy.evaluate(facts(strategy, List.of(job("work", true, WorkerJobStatus.SUCCEEDED)), true,
                List.of(PlanAcceptanceVerdict.PASS), true));
        assertThat(decision.stage()).isEqualTo(PlanJoinStage.WAITING_EFFECTS);
        assertThat(decision.outcome()).isNull();
    }

    @ParameterizedTest
    @EnumSource(PlanJoinPolicy.class)
    void optionalFailureDoesNotOverrideSatisfiedAcceptance(PlanJoinPolicy strategy) {
        var decision = policy.evaluate(facts(strategy, List.of(job("work", true, WorkerJobStatus.SUCCEEDED),
                job("extra", false, WorkerJobStatus.FAILED)), false, List.of(PlanAcceptanceVerdict.PASS), true));
        assertThat(decision.stage()).isEqualTo(PlanJoinStage.READY);
        assertThat(decision.outcome()).isEqualTo(GoalTaskStatus.SUCCEEDED);
    }

    @ParameterizedTest
    @EnumSource(PlanJoinPolicy.class)
    void requiredFailureNeverSucceeds(PlanJoinPolicy strategy) {
        for (var status : List.of(WorkerJobStatus.FAILED, WorkerJobStatus.PARTIAL, WorkerJobStatus.CANCELLED, WorkerJobStatus.SKIPPED, WorkerJobStatus.SUPERSEDED)) {
            var jobs = List.of(job("work", true, status));
            assertThat(policy.evaluate(facts(strategy, jobs, false, List.of(PlanAcceptanceVerdict.PASS), true)).outcome())
                    .isEqualTo(GoalTaskStatus.PARTIAL);
            assertThat(policy.evaluate(facts(strategy, jobs, false, List.of(PlanAcceptanceVerdict.PASS), false)).outcome())
                    .isEqualTo(GoalTaskStatus.FAILED);
        }
    }

    @ParameterizedTest
    @EnumSource(PlanJoinPolicy.class)
    void missingOrUnsuccessfulAcceptanceNeverSucceeds(PlanJoinPolicy strategy) {
        var jobs = List.of(job("work", true, WorkerJobStatus.SUCCEEDED));
        for (List<PlanAcceptanceVerdict> verdicts : List.of(List.<PlanAcceptanceVerdict>of(),
                List.of(PlanAcceptanceVerdict.UNKNOWN), List.of(PlanAcceptanceVerdict.PASS, PlanAcceptanceVerdict.FAIL))) {
            assertThat(policy.evaluate(facts(strategy, jobs, false, verdicts, true)).outcome()).isEqualTo(GoalTaskStatus.PARTIAL);
        }
        assertThat(policy.evaluate(facts(strategy, jobs, false, List.of(PlanAcceptanceVerdict.PASS), false)).outcome())
                .isEqualTo(GoalTaskStatus.FAILED);
    }

    @ParameterizedTest
    @EnumSource(PlanJoinPolicy.class)
    void rejectsOldDuplicateOrEmptyPlanFacts(PlanJoinPolicy strategy) {
        for (var jobs : List.of(List.<PlanJoinJobBO>of(), List.of(job("optional", false, WorkerJobStatus.SUCCEEDED)),
                List.of(new PlanJoinJobBO("old", 1, true, WorkerJobStatus.SUCCEEDED)),
                List.of(job("same", true, WorkerJobStatus.SUCCEEDED), job("same", false, WorkerJobStatus.FAILED)))) {
            assertThatThrownBy(() -> policy.evaluate(facts(strategy, jobs, false, List.of(PlanAcceptanceVerdict.PASS), true)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    private PlanJoinJobBO job(String id, boolean required, WorkerJobStatus status) {
        return new PlanJoinJobBO(id, 2, required, status);
    }

    private PlanJoinFactsBO facts(PlanJoinPolicy strategy, List<PlanJoinJobBO> jobs, boolean effects,
                                  List<PlanAcceptanceVerdict> verdicts, boolean deliverable) {
        return new PlanJoinFactsBO(2, strategy, jobs, effects, verdicts, deliverable);
    }
}
