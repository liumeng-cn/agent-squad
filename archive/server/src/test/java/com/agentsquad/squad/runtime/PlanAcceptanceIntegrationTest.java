package com.agentsquad.squad.runtime;

import com.agentsquad.squad.mapper.ProjectionMapper;
import com.agentsquad.squad.service.ConversationQueryService;
import com.agentsquad.squad.service.TaskFinalizerService;
import com.agentsquad.squad.service.TaskSnapshotService;
import com.agentsquad.squad.AgentSquadApplication;
import com.agentsquad.squad.enums.AccountRole;
import com.agentsquad.squad.enums.AcceptanceVerificationType;
import com.agentsquad.squad.enums.DeterministicAcceptanceCheck;
import com.agentsquad.squad.exceptions.RuntimeBusinessException;
import com.agentsquad.squad.model.bo.*;
import com.agentsquad.squad.service.GoalTaskService;
import com.agentsquad.squad.identity.AuthView;
import com.agentsquad.squad.service.InteractionService;
import com.agentsquad.squad.enums.PlanAcceptanceVerdict;
import com.agentsquad.squad.enums.PlanJoinStage;
import com.agentsquad.squad.service.CompletionProposalService;
import com.agentsquad.squad.service.ContextSnapshotService;
import com.agentsquad.squad.service.PlanAcceptanceService;
import com.agentsquad.squad.service.PlanCompletionEvaluationService;
import com.agentsquad.squad.service.PlanService;
import com.agentsquad.squad.testsupport.PostgresTestDatabase;
import com.agentsquad.squad.model.dto.WorkerDefinitionCreateDTO;
import com.agentsquad.squad.service.WorkerDefinitionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 当前计划逐项验收的真实事务验证
 * <p>
 * 在独占数据库核对程序检查、模型证据、用户确认、拒绝与旧版本隔离，不调用真实模型。
 */
class PlanAcceptanceIntegrationTest {
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void checksEvidenceAndRealUserDecision(boolean accepted, @TempDir Path directory) throws Exception {
        Long user = 1L;
        var actor = new AuthView(user, "tester", List.of(AccountRole.ADMIN), "csrf");
        try (var context = SpringApplication.run(AgentSquadApplication.class,
                "--spring.main.web-application-type=" + ("true".equals(System.getenv("SQUAD_ACCEPTANCE_BROWSER_PROBE")) ? "servlet" : "none"),
                "--server.port=0", "--server.shutdown=immediate", "--agentsquad.identity.allow-unsigned-local=true",
                "--spring.datasource.url=" + PostgresTestDatabase.url(directory),
                "--spring.datasource.username=" + PostgresTestDatabase.username(),
                "--spring.datasource.password=" + PostgresTestDatabase.password(),
                "--agentsquad.delivery.enabled=true", "--agentsquad.delivery.poll-delay-ms=3600000",
                "--agentsquad.projection.poll-delay-ms=3600000", "--agentsquad.model.api-key=test-only",
                "--agentsquad.model.base-url=http://127.0.0.1:1/v1", "--agentsquad.model.name=test-model")) {
            var db = context.getBean(JdbcTemplate.class);
            var json = context.getBean(ObjectMapper.class);
            var tasks = context.getBean(GoalTaskService.class);
            var conversation = tasks.createConversation(user, "逐项验收");
            var task = tasks.submit(user, conversation.conversationId(), "acceptance", "写作并核对结果");
            db.update("UPDATE sq_command SET status='CANCELLED'");
            String session = db.queryForObject("SELECT framework_session_id FROM sq_runtime_binding WHERE task_id=? AND role='MASTER'", String.class, task.taskId());
            Long binding = db.queryForObject("SELECT binding_id FROM sq_runtime_binding WHERE task_id=? AND role='MASTER'", Long.class, task.taskId());
            context.getBean(WorkerDefinitionService.class).publish(actor, "acceptance-writer",
                    new WorkerDefinitionCreateDTO("acceptance-writer", "1.0.0", "写作", json.readTree("{}"),
                            json.readTree("{}"), List.of(), "default", "default"));
            Long snapshot = context.getBean(ContextSnapshotService.class).prepare(user, session, "write", "context").snapshotId();
            Long optionalSnapshot = context.getBean(ContextSnapshotService.class).prepare(user, session, "optional", "optional-context").snapshotId();
            var nodes = List.of(new PlanNodeBO("write", "acceptance-writer", "1.0.0", "完成短文", snapshot, List.of(), true, 1),
                    new PlanNodeBO("optional", "acceptance-writer", "1.0.0", "额外润色", optionalSnapshot, List.of(), false, 1));
            var criteria = List.of(
                    new PlanAcceptanceCriterionBO("saved", "必要工作均成功且结果保存", true, AcceptanceVerificationType.DETERMINISTIC, false,
                            DeterministicAcceptanceCheck.REQUIRED_RESULTS_READY),
                    new PlanAcceptanceCriterionBO("quality", "文字符合要求", true, AcceptanceVerificationType.MODEL_REVIEW, false, null),
                    new PlanAcceptanceCriterionBO("confirm", "请用户确认结果", true, AcceptanceVerificationType.USER_REVIEW, false, null));
            context.getBean(PlanService.class).publish(user, session, new PlanPublishBO("plan", 0, nodes, "ALL_REQUIRED", criteria));
            var publicPlan = json.readTree(db.queryForObject("SELECT payload_json FROM sq_conversation_event WHERE conversation_id=? AND event_type='plan.published'",
                    String.class, conversation.conversationId()));
            assertThat(publicPlan.path("acceptanceCriteria")).hasSize(3);
            assertThat(publicPlan.path("joinPolicy").asText()).isEqualTo("ALL_REQUIRED");
            db.update("UPDATE sq_command SET status='CANCELLED'");
            var evaluation = context.getBean(PlanCompletionEvaluationService.class);
            var acceptance = context.getBean(PlanAcceptanceService.class);
            assertThat(evaluation.evaluate(user, task.taskId(), 1, List.of()).decision().stage()).isEqualTo(PlanJoinStage.WAITING_WORK);
            assertThatThrownBy(() -> acceptance.prepareUserReview(user, session, new AcceptanceReviewRequestBO("confirm", 1)))
                    .isInstanceOf(RuntimeBusinessException.class);
            db.update("UPDATE sq_worker_job SET status='SUCCEEDED',result_json='{\"text\":\"test-result\"}' WHERE task_id=?", task.taskId());
            db.update("UPDATE sq_worker_job SET status='RUNNING' WHERE task_id=? AND node_key='optional'", task.taskId());
            assertThat(evaluation.evaluate(user, task.taskId(), 1, List.of()).decision().stage()).isEqualTo(PlanJoinStage.WAITING_WORK);
            db.update("UPDATE sq_worker_job SET status='FAILED',result_json=NULL WHERE task_id=? AND node_key='optional'", task.taskId());
            db.update("UPDATE sq_goal_task SET status='RUNNING' WHERE task_id=?", task.taskId());
            Long job = db.queryForObject("SELECT job_id FROM sq_worker_job WHERE task_id=? AND node_key='write'", Long.class, task.taskId());
            var unknown = evaluation.evaluate(user, task.taskId(), 1, List.of());
            assertThat(context.getBean(TaskSnapshotService.class).listJobs(user, task.taskId()).items())
                    .anyMatch(item -> item.nodeKey().equals("optional") && Boolean.FALSE.equals(item.required()));
            assertThat(unknown.acceptanceResults()).extracting(result -> result.verdict())
                    .containsExactly(PlanAcceptanceVerdict.PASS, PlanAcceptanceVerdict.UNKNOWN, PlanAcceptanceVerdict.UNKNOWN);
            var completion = context.getBean(CompletionProposalService.class);
            assertThatThrownBy(() -> completion.submit(new CompletionSubmissionBO(user, session, "turn", "call", "SUCCEEDED", "短文", List.of())))
                    .isInstanceOf(RuntimeBusinessException.class);
            assertThatThrownBy(() -> evaluation.evaluate(user, task.taskId(), 1,
                    List.of(new AcceptanceReviewBO("quality", PlanAcceptanceVerdict.PASS, List.of(-1L)))))
                    .isInstanceOf(RuntimeBusinessException.class);
            var interactions = context.getBean(InteractionService.class);
            var review = interactions.bindFrameworkRequest(user, task.taskId(), binding, null, "turn-review", "review-request",
                    Map.of("name", "request_acceptance_review", "input", Map.of("criterionId", "confirm", "expectedPlanVersion", 1)));
            assertThat(review).isNotNull();
            assertThat(interactions.bindFrameworkRequest(user, task.taskId(), binding, null, "turn-review", "review-request",
                    Map.of("name", "request_acceptance_review", "input", Map.of("criterionId", "confirm", "expectedPlanVersion", 1))).interactionId())
                    .isEqualTo(review.interactionId());
            assertThat(evaluation.evaluate(user, task.taskId(), 1, List.of()).decision().stage()).isEqualTo(PlanJoinStage.WAITING_EFFECTS);
            interactions.respond(actor, review.interactionId(), "answer-review", 0, "INPUT", null, null, Map.of("accepted", accepted));
            var reviews = List.of(new AcceptanceReviewBO("quality", PlanAcceptanceVerdict.PASS, List.of(job)),
                    new AcceptanceReviewBO("confirm", PlanAcceptanceVerdict.PASS, List.of(review.interactionId())));
            var checked = evaluation.evaluate(user, task.taskId(), 1, reviews);
            assertThat(checked.acceptanceResults().get(2).verdict()).isEqualTo(accepted ? PlanAcceptanceVerdict.PASS : PlanAcceptanceVerdict.FAIL);
            if (!accepted) {
                assertThatThrownBy(() -> completion.submit(new CompletionSubmissionBO(user, session, "turn", "call", "SUCCEEDED", "短文", reviews)))
                        .isInstanceOf(RuntimeBusinessException.class);
            }
            completion.submit(new CompletionSubmissionBO(user, session, "turn", "call", accepted ? "SUCCEEDED" : "PARTIAL", "短文", reviews));
            var proposal = json.readTree(db.queryForObject("SELECT completion_proposal_json FROM sq_goal_task WHERE task_id=?", String.class, task.taskId()));
            assertThat(proposal.path("acceptanceResults")).hasSize(3);
            assertThat(proposal.path("acceptanceResults").get(2).path("verifier").asText()).isEqualTo("USER:" + user);
            if (accepted && "true".equals(System.getenv("SQUAD_ACCEPTANCE_BROWSER_PROBE"))) {
                var target = context.getBean(ProjectionMapper.class).findTarget(user, binding);
                assertThat(context.getBean(TaskFinalizerService.class).tryComplete(target,
                        new NativeEventViewBO(1, "finished", "turn/output", "run",
                                "turn", "completed", System.currentTimeMillis(), Map.of()))).isTrue();
                var views = context.getBean(TaskSnapshotService.class).listJobs(user, task.taskId());
                assertThat(views.acceptanceChecks()).hasSize(3);
                assertThat(context.getBean(ConversationQueryService.class)
                        .snapshot(user, conversation.conversationId(), null, 30).taskStates())
                        .extracting(state -> state.getTaskId()).contains(task.taskId());
                var ready = Path.of("/tmp/squad-browser-probe.json");
                var done = Path.of("/tmp/squad-acceptance-browser-done");
                java.nio.file.Files.deleteIfExists(done);
                int port = ((org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext) context).getWebServer().getPort();
                java.nio.file.Files.writeString(ready, "{\"port\":" + port + ",\"conversationId\":\"" + conversation.conversationId() + "\"}");
                try {
                    for (int attempt = 0; attempt < 3000 && !java.nio.file.Files.exists(done); attempt++) {
                        Thread.sleep(100);
                    }
                    assertThat(java.nio.file.Files.exists(done)).isTrue();
                } finally {
                    java.nio.file.Files.deleteIfExists(ready);
                    java.nio.file.Files.deleteIfExists(done);
                }
                return;
            }
            context.getBean(PlanService.class).publish(user, session, new PlanPublishBO("replacement", 1, nodes, "ALL_REQUIRED", criteria));
            var snapshotService = context.getBean(TaskSnapshotService.class);
            assertThat(snapshotService.listJobs(user, task.taskId(), 1L).items()).allMatch(item -> item.planVersion() == 1);
            assertThat(snapshotService.listJobs(user, task.taskId()).items()).anyMatch(item -> job.equals(item.reusedFromJobId()));
            var publicReuse = json.readTree(db.queryForObject("SELECT payload_json FROM sq_conversation_event WHERE conversation_id=? AND event_type='job.reused' ORDER BY id DESC LIMIT 1",
                    String.class, conversation.conversationId()));
            assertThat(publicReuse.path("reusedFromJobId").asText()).isEqualTo(String.valueOf(job));
            assertThat(publicReuse.path("planVersion").asInt()).isEqualTo(2);
            assertThat(snapshotService.listJobs(user, task.taskId(), 1L).acceptanceChecks()).isEmpty();
            assertThatThrownBy(() -> snapshotService.listJobs(-1L, task.taskId(), 1L)).isInstanceOf(RuntimeBusinessException.class);
            assertThatThrownBy(() -> snapshotService.listJobs(user, task.taskId(), 3L)).isInstanceOf(RuntimeBusinessException.class);
            assertThatThrownBy(() -> acceptance.prepareUserReview(user, session, new AcceptanceReviewRequestBO("confirm", 1)))
                    .isInstanceOf(RuntimeBusinessException.class);
            assertThatThrownBy(() -> evaluation.evaluate(user, task.taskId(), 2, reviews)).isInstanceOf(RuntimeBusinessException.class);
            var old = json.readValue("{\"logicalKey\":\"old\",\"expectedPlanVersion\":0,\"nodes\":[],\"joinPolicy\":\"ALL_REQUIRED\",\"acceptanceCriteria\":[\"old text\"]}", PlanPublishBO.class);
            assertThat(old.acceptanceCriteria().get(0).legacy()).isTrue();
            db.update("UPDATE sq_worker_job SET status='FAILED',result_json=NULL WHERE task_id=? AND plan_version=2 AND node_key='optional'", task.taskId());
            completion.submit(new CompletionSubmissionBO(user, session, "turn-new", "call-new", "PARTIAL", "保留已有结果", List.of()));
            var target = context.getBean(ProjectionMapper.class).findTarget(user, binding);
            assertThat(context.getBean(TaskFinalizerService.class).tryComplete(target,
                    new NativeEventViewBO(1, "old-ended", "turn/output", "run",
                            "turn", "completed", System.currentTimeMillis(), Map.of()))).isFalse();
            assertThat(db.queryForObject("SELECT status FROM sq_goal_task WHERE task_id=?", String.class, task.taskId()))
                    .isEqualTo("RUNNING");
            db.update("UPDATE sq_worker_job SET status='RECOVERY_BLOCKED' WHERE task_id=? AND plan_version=2", task.taskId());
            assertThat(context.getBean(TaskFinalizerService.class).tryComplete(target,
                    new NativeEventViewBO(1, "ended", "turn/output", "run",
                            "turn-new", "completed", System.currentTimeMillis(), Map.of()))).isTrue();
            assertThat(db.queryForObject("SELECT status FROM sq_goal_task WHERE task_id=?", String.class, task.taskId()))
                    .isEqualTo("RECOVERY_BLOCKED");
            assertThat(db.queryForObject("SELECT COUNT(*) FROM sq_task_event WHERE task_id=? AND event_type='answer.completed'",
                    Integer.class, task.taskId())).isZero();
        }
    }
}
