package com.agentsquad.squad.presentation;

import com.agentsquad.squad.AgentSquadApplication;
import com.agentsquad.squad.artifact.ArtifactService;
import com.agentsquad.squad.conversation.service.GoalTaskService;
import com.agentsquad.squad.identity.AuthView;
import com.agentsquad.squad.interaction.repository.InteractionMapper;
import com.agentsquad.squad.interaction.service.InputFormSchema;
import com.agentsquad.squad.runtime.dto.PlanNode;
import com.agentsquad.squad.runtime.dto.PlanPublish;
import com.agentsquad.squad.runtime.service.ContextSnapshotService;
import com.agentsquad.squad.runtime.service.PlanService;
import com.agentsquad.squad.worker.WorkerDefinitionService;
import com.agentsquad.squad.worker.dto.WorkerDefinitionCreateRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.SpringApplication;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 本地任务页面的固定状态夹具服务器。
 * <p>
 * 仅用于浏览器检查等待态、子任务列表、附件和核对 UI，不执行模型；不能作为原生执行实验的证据。
 */
public final class TaskPageReviewServer {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("agentsquad-page-review-");
        var context = SpringApplication.run(AgentSquadApplication.class,
                "--server.port=18080", "--spring.datasource.url=jdbc:h2:file:" + directory.resolve("squad")
                        + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE",
                "--spring.datasource.username=sa", "--spring.datasource.driver-class-name=org.h2.Driver",
                "--agentsquad.auth.username=review", "--agentsquad.auth.password=local-review",
                "--agentsquad.auth.role=OPERATOR", "--agentsquad.model.api-key=test-only",
                "--agentsquad.model.name=test-model", "--agentsquad.model.base-url=http://127.0.0.1:1/v1",
                "--agentsquad.delivery.enabled=true", "--agentsquad.delivery.poll-delay-ms=3600000",
                "--agentsquad.projection.poll-delay-ms=3600000");
        String userId = "00000000-0000-0000-0000-000000000001";
        var tasks = context.getBean(GoalTaskService.class);
        var json = context.getBean(ObjectMapper.class);
        var jdbc = context.getBean(JdbcTemplate.class);
        var file = context.getBean(ArtifactService.class).upload(userId, "review-evidence", "写作要求.txt",
                "页面夹具：面向开发者写作，保留引用。".getBytes(StandardCharsets.UTF_8));
        var conversation = tasks.createConversation(userId, "页面验收夹具 · 多任务写作");
        var task = tasks.submit(userId, conversation.conversationId(), "review-goal", "撰写文章并完成审阅（页面状态夹具）", List.of(file.artifactId()));
        String master = jdbc.queryForObject("SELECT framework_session_id FROM sq_runtime_binding WHERE task_id = ?", String.class, task.taskId());
        String binding = jdbc.queryForObject("SELECT binding_id FROM sq_runtime_binding WHERE task_id = ?", String.class, task.taskId());
        context.getBean(WorkerDefinitionService.class).publish(new AuthView(userId, "review", List.of("ADMIN"), "fixture"),
                "review-writer", new WorkerDefinitionCreateRequest("writer", "1.0.0", "局部内容写作", json.readTree("{}"), json.readTree("{}"), List.of(), "default", "default"));
        var snapshots = context.getBean(ContextSnapshotService.class);
        String first = snapshots.prepare(userId, master, "draft", "review-draft").snapshotId();
        String second = snapshots.prepare(userId, master, "review", "review-check").snapshotId();
        var plan = context.getBean(PlanService.class).publish(userId, master, new PlanPublish("review-plan", 0,
                List.of(new PlanNode("draft", "writer", "1.0.0", "起草文章", first, List.of(), true, 1),
                        new PlanNode("review", "writer", "1.0.0", "审阅文章", second, List.of(), true, 1)),
                "ALL_REQUIRED", List.of("文章与审阅均完成")));
        jdbc.update("UPDATE sq_command SET status = 'CANCELLED' WHERE task_id = ?", task.taskId());
        jdbc.update("UPDATE sq_worker_job SET status = 'SUCCEEDED', result_json = ? WHERE job_id = ?",
                "{\"text\":\"草稿已就绪，等待审阅\"}", plan.jobIds().getFirst());
        jdbc.update("UPDATE sq_worker_job SET status = 'WAITING_INPUT' WHERE job_id = ?", plan.jobIds().get(1));
        jdbc.update("UPDATE sq_goal_task SET status = 'WAITING_INPUT' WHERE task_id = ?", task.taskId());
        String schema = context.getBean(InputFormSchema.class).normalize(json.readTree("""
                {"type":"object","properties":{"tone":{"type":"string","title":"文章文风","enum":["正式","轻松"]}},"required":["tone"]}
                """)).toString();
        context.getBean(InteractionMapper.class).insert(UUID.randomUUID().toString(), userId, task.taskId(), plan.jobIds().get(1),
                binding, null, "FRAMEWORK", "review-input", "INPUT", "请确认文章采用哪种文风", schema, 1,
                "review-hash", 0, LocalDateTime.now().plusHours(1), userId, "review");
        String operationId = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO sq_operation (operation_id, user_id, task_id, binding_id, framework_turn_id,
                    tool_call_id, tool_key, tool_version, effect, request_hash, arguments_json, status, scope_id)
                VALUES (?, ?, ?, ?, 'review-turn', 'review-call', 'mock_ticket_create', 'v1', 'WRITE', 'review-hash', ?, 'UNKNOWN', (SELECT scope_id FROM sq_runtime_binding WHERE binding_id = ?))
                """, operationId, userId, task.taskId(), binding, "{\"title\":\"示例操作\",\"password\":\"hidden\"}", binding);
        jdbc.update("INSERT INTO sq_operation_reconciler (user_id, task_id, scope_id) SELECT user_id, task_id, scope_id FROM sq_goal_task WHERE user_id = ? AND task_id = ?", userId, task.taskId());
        Files.writeString(Path.of("/private/tmp/agentsquad-page-review-task"), task.taskId());
        Files.writeString(Path.of("/private/tmp/agentsquad-page-review-conversation"), conversation.conversationId());
    }
}
