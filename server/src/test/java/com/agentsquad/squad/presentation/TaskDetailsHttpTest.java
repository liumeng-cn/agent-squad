package com.agentsquad.squad.presentation;

import com.agentsquad.squad.AgentSquadApplication;
import com.agentsquad.squad.artifact.ArtifactService;
import com.agentsquad.squad.artifact.ArtifactView;
import com.agentsquad.squad.conversation.dto.TaskReceipt;
import com.agentsquad.squad.conversation.service.GoalTaskService;
import com.agentsquad.squad.identity.AuthService;
import com.agentsquad.squad.identity.AuthView;
import com.agentsquad.squad.interaction.repository.InteractionMapper;
import com.agentsquad.squad.interaction.service.InputFormSchema;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 任务详情的真实 HTTP 契约与权限验收。
 * <p>
 * 覆盖附件原子上传、资源隔离、核对显式授权、交互 Schema/幂等和正文按版本替换，不调用模型。
 */
class TaskDetailsHttpTest {
    @Test
    void protectsArtifactsReconciliationAndInteraction(@TempDir Path directory) throws Exception {
        try (ConfigurableApplicationContext context = SpringApplication.run(AgentSquadApplication.class,
                "--server.port=0", "--spring.datasource.url=jdbc:h2:file:" + directory.resolve("squad")
                        + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE",
                "--spring.datasource.username=sa", "--spring.datasource.driver-class-name=org.h2.Driver",
                "--agentsquad.auth.username=tester", "--agentsquad.auth.password=test-only",
                "--agentsquad.model.api-key=test-only", "--agentsquad.model.base-url=http://127.0.0.1:1/v1",
                "--agentsquad.model.name=test-model",
                "--agentsquad.delivery.enabled=true", "--agentsquad.delivery.poll-delay-ms=600000",
                "--agentsquad.projection.poll-delay-ms=600000")) {
            MockMvc mvc = MockMvcBuilders.webAppContextSetup((WebApplicationContext) context).build();
            AuthService auth = context.getBean(AuthService.class);
            String token = auth.login("tester", "test-only");
            AuthView actor = auth.authenticate(token);
            Cookie cookie = new Cookie("SQUAD_SESSION", token);
            ObjectMapper json = context.getBean(ObjectMapper.class);
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            byte[] bytes = "验收证据：本地验证".getBytes(StandardCharsets.UTF_8);
            MockMultipartFile file = new MockMultipartFile("file", "evidence.txt", "text/plain", bytes);
            String body = mvc.perform(multipart("/api/v1/artifacts").file(file).cookie(cookie)
                            .header("X-CSRF-Token", actor.csrfToken()).header("Idempotency-Key", "upload-1"))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
            String artifactId = json.readTree(body).path("artifactId").asText();
            assertThat(body).doesNotContain("content_bytes", "userId", "uploadKey");
            mvc.perform(multipart("/api/v1/artifacts").file(file).cookie(cookie)
                            .header("X-CSRF-Token", actor.csrfToken()).header("Idempotency-Key", "upload-1"))
                    .andExpect(status().isCreated());
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_artifact", Integer.class)).isEqualTo(1);
            mvc.perform(multipart("/api/v1/artifacts").file(new MockMultipartFile("file", "other.txt", "text/plain", bytes))
                            .cookie(cookie).header("X-CSRF-Token", actor.csrfToken()).header("Idempotency-Key", "upload-1"))
                    .andExpect(status().isConflict());
            mvc.perform(multipart("/api/v1/artifacts").file(new MockMultipartFile("file", "bad.html", "text/html", bytes))
                            .cookie(cookie).header("X-CSRF-Token", actor.csrfToken()).header("Idempotency-Key", "bad-type"))
                    .andExpect(status().isBadRequest());
            mvc.perform(multipart("/api/v1/artifacts").file(file).cookie(cookie).header("Idempotency-Key", "csrf-missing"))
                    .andExpect(status().isUnauthorized());
            assertThat(mvc.perform(get("/api/v1/artifacts/{id}/content", artifactId).cookie(cookie))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray()).isEqualTo(bytes);
            ArtifactView corrupted = context.getBean(ArtifactService.class).upload(actor.userId(), "corrupt-check",
                    "corrupt.txt", "before".getBytes(StandardCharsets.UTF_8));
            jdbc.update("UPDATE sq_artifact SET content_bytes = ? WHERE artifact_id = ?",
                    "after".getBytes(StandardCharsets.UTF_8), corrupted.artifactId());
            mvc.perform(get("/api/v1/artifacts/{id}/content", corrupted.artifactId()).cookie(cookie))
                    .andExpect(status().isConflict());
            ReflectionTestUtils.setField(auth, "configuredUserId", "00000000-0000-0000-0000-000000000002");
            Cookie other = new Cookie("SQUAD_SESSION", auth.login("tester", "test-only"));
            mvc.perform(get("/api/v1/artifacts/{id}/content", artifactId).cookie(other)).andExpect(status().isNotFound());
            mvc.perform(get("/api/v1/artifacts/{id}/content", UUID.randomUUID()).cookie(cookie)).andExpect(status().isNotFound());

            GoalTaskService tasks = context.getBean(GoalTaskService.class);
            String conversation = tasks.createConversation(actor.userId(), "页面验收").conversationId();
            mvc.perform(post("/api/v1/conversations/{id}/tasks", conversation).cookie(cookie)
                            .header("X-CSRF-Token", actor.csrfToken()).header("Idempotency-Key", "invalid-attachment")
                            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                                    "message", "引用不存在的文件", "artifactIds", List.of(UUID.randomUUID().toString())))))
                    .andExpect(status().isNotFound());
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_goal_task", Integer.class)).isZero();
            String taskBody = mvc.perform(post("/api/v1/conversations/{id}/tasks", conversation).cookie(cookie)
                            .header("X-CSRF-Token", actor.csrfToken()).header("Idempotency-Key", "task-1")
                            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                                    "message", "写一篇文章", "artifactIds", List.of(artifactId)))))
                    .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
            String taskId = json.readTree(taskBody).path("taskId").asText();
            jdbc.update("UPDATE sq_command SET status = 'CANCELLED' WHERE task_id = ?", taskId);
            assertThat(mvc.perform(get("/api/v1/tasks/{id}/artifacts", taskId).cookie(cookie))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).contains(artifactId);
            mvc.perform(get("/api/v1/tasks/{id}/jobs", taskId).cookie(cookie)).andExpect(status().isOk());
            mvc.perform(get("/api/v1/tasks/{id}/jobs", taskId).cookie(other)).andExpect(status().isNotFound());
            String binding = jdbc.queryForObject("SELECT binding_id FROM sq_runtime_binding WHERE task_id = ?", String.class, taskId);
            String operationId = UUID.randomUUID().toString();
            jdbc.update("""
                            INSERT INTO sq_operation (operation_id, user_id, task_id, binding_id, framework_turn_id,
                              tool_call_id, tool_key, tool_version, effect, request_hash, arguments_json, status)
                            VALUES (?, ?, ?, ?, 'turn-1', 'call-1', 'mock_ticket_create', 'v1', 'WRITE', 'hash-1', ?, 'UNKNOWN')
                            """, operationId, actor.userId(), taskId, binding,
                    "{\"title\":\"private-title\",\"password\":\"must-not-leak\"}");
            String detail = mvc.perform(get("/api/v1/operations/{id}", operationId).cookie(cookie))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(detail).doesNotContain("private-title", "must-not-leak", "frameworkTurnId");
            assertThat(json.readTree(detail).path("canReconcile").asBoolean()).isFalse();
            String reconcile = json.writeValueAsString(Map.of("expectedVersion", 0, "disposition", "UNRESOLVED",
                    "evidenceRefs", List.of(artifactId), "note", "仍需进一步确认"));
            mvc.perform(post("/api/v1/operations/{id}/reconcile", operationId).cookie(cookie)
                            .header("X-CSRF-Token", actor.csrfToken()).contentType(MediaType.APPLICATION_JSON).content(reconcile))
                    .andExpect(status().isForbidden());
            ReflectionTestUtils.setField(auth, "configuredUserId", actor.userId());
            ReflectionTestUtils.setField(auth, "configuredRole", "OPERATOR");
            String operatorToken = auth.login("tester", "test-only");
            AuthView operator = auth.authenticate(operatorToken);
            Cookie operatorCookie = new Cookie("SQUAD_SESSION", operatorToken);
            mvc.perform(post("/api/v1/operations/{id}/reconcile", operationId).cookie(operatorCookie)
                            .header("X-CSRF-Token", operator.csrfToken()).contentType(MediaType.APPLICATION_JSON).content(reconcile))
                    .andExpect(status().isForbidden());
            jdbc.update("INSERT INTO sq_operation_reconciler (user_id, task_id) VALUES (?, ?)", actor.userId(), taskId);
            mvc.perform(post("/api/v1/operations/{id}/reconcile", operationId).cookie(operatorCookie)
                            .header("X-CSRF-Token", operator.csrfToken()).contentType(MediaType.APPLICATION_JSON).content(reconcile))
                    .andExpect(status().isOk());
            assertThat(jdbc.queryForObject("SELECT status FROM sq_operation WHERE operation_id = ?", String.class, operationId)).isEqualTo("UNKNOWN");
            mvc.perform(post("/api/v1/operations/{id}/reconcile", operationId).cookie(operatorCookie)
                            .header("X-CSRF-Token", operator.csrfToken()).contentType(MediaType.APPLICATION_JSON).content(reconcile))
                    .andExpect(status().isConflict());

            String interactionId = UUID.randomUUID().toString();
            String schema = context.getBean(InputFormSchema.class).normalize(json.readTree("""
                    {"type":"object","properties":{"style":{"type":"string","enum":["正式","轻松"]}},"required":["style"]}
                    """)).toString();
            context.getBean(InteractionMapper.class).insert(interactionId, actor.userId(), taskId, null,
                    binding, null, "FRAMEWORK", "input-1", "INPUT", "请选择文风", schema, 0,
                    "input-hash", 0, LocalDateTime.now().plusHours(1), actor.userId(), "tester");
            String response = json.writeValueAsString(Map.of("expectedVersion", 0,
                    "answer", Map.of("kind", "INPUT", "value", Map.of("style", "正式"))));
            mvc.perform(post("/api/v1/interactions/{id}/responses", interactionId).cookie(cookie)
                            .header("X-CSRF-Token", actor.csrfToken()).header("Idempotency-Key", "answer-1")
                            .contentType(MediaType.APPLICATION_JSON).content(response.replace("正式", "不允许")))
                    .andExpect(status().isBadRequest());
            for (int i = 0; i < 2; i++) {
                mvc.perform(post("/api/v1/interactions/{id}/responses", interactionId).cookie(cookie)
                        .header("X-CSRF-Token", actor.csrfToken()).header("Idempotency-Key", "answer-1")
                        .contentType(MediaType.APPLICATION_JSON).content(response)).andExpect(status().isAccepted());
            }
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_command WHERE action = 'RESPOND'", Integer.class)).isEqualTo(1);
            var events = context.getBean(com.agentsquad.squad.conversation.repository.TaskEventMapper.class);
            events.insertProjected(actor.userId(), taskId, 2, "answer-old", "answer.completed",
                    "{\"text\":\"旧回答\",\"revision\":0}", LocalDateTime.now());
            events.insertProjected(actor.userId(), taskId, 3, "answer-new", "answer.replaced",
                    "{\"text\":\"新回答\",\"revision\":1}", LocalDateTime.now());
            jdbc.update("UPDATE sq_goal_task SET answer_revision = 1, event_seq = 3, status = 'PARTIAL' WHERE task_id = ?", taskId);
            String snapshot = mvc.perform(get("/api/v1/tasks/{id}/snapshot", taskId).cookie(cookie))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(json.readTree(snapshot).path("answer").path("text").asText()).isEqualTo("新回答");
            assertThat(json.readTree(snapshot).path("answer").path("completed").asBoolean()).isFalse();
            assertThat(json.readTree(snapshot).path("task").path("status").asText()).isEqualTo("PARTIAL");
            jdbc.update("UPDATE sq_conversation SET status = 'DELETING' WHERE conversation_id = ?", conversation);
            mvc.perform(get("/api/v1/artifacts/{id}/content", artifactId).cookie(cookie)).andExpect(status().isNotFound());
        }
    }
}
