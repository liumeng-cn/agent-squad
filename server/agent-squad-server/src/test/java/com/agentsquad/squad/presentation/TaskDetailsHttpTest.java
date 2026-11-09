package com.agentsquad.squad.presentation;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.agentsquad.squad.mapper.TaskEventMapper;
import com.agentsquad.squad.testsupport.PostgresTestDatabase;
import com.agentsquad.squad.AgentSquadApplication;
import com.agentsquad.squad.enums.AccountRole;
import com.agentsquad.squad.enums.ApiCode;
import com.agentsquad.squad.identity.AuthView;
import com.agentsquad.squad.mapper.InteractionMapper;
import com.agentsquad.squad.model.vo.ArtifactVO;
import com.agentsquad.squad.service.ArtifactService;
import com.agentsquad.squad.service.GoalTaskService;
import com.agentsquad.squad.service.InputFormSchemaService;
import com.agentsquad.squad.testsupport.ApiResultJson;
import com.agentsquad.squad.testsupport.TestAssertion;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
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
                "--server.port=0", "--spring.datasource.url=" + PostgresTestDatabase.url(directory.resolve("squad")),
                "--spring.datasource.username=" + PostgresTestDatabase.username(), "--spring.datasource.password=" + PostgresTestDatabase.password(), "--spring.datasource.driver-class-name=org.postgresql.Driver",
                "--agentsquad.auth.username=tester", "--agentsquad.auth.password=test-only",
                "--agentsquad.model.api-key=test-only", "--agentsquad.model.base-url=http://127.0.0.1:1/v1",
                "--agentsquad.model.name=test-model",
                "--agentsquad.delivery.enabled=true", "--agentsquad.delivery.poll-delay-ms=600000",
                "--agentsquad.projection.poll-delay-ms=600000")) {
            MockMvc mvc = MockMvcBuilders.webAppContextSetup((WebApplicationContext) context).build();
            Long userId = 1L;
            AuthView actor = new AuthView(userId, "tester", java.util.List.of(AccountRole.USER), null);
            ObjectMapper json = context.getBean(ObjectMapper.class);
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            byte[] bytes = "验收证据：本地验证".getBytes(StandardCharsets.UTF_8);
            MockMultipartFile file = new MockMultipartFile("file", "evidence.txt", "text/plain", bytes);
            String body = mvc.perform(multipart("/api/v1/artifacts").file(file).with(TestAssertion.as(userId, "USER"))
                            .header("Idempotency-Key", "upload-1"))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
            Long artifactId = ApiResultJson.data(json, body).path("artifactId").asLong();
            assertThat(body).doesNotContain("content_bytes", "userId", "uploadKey");
            mvc.perform(multipart("/api/v1/artifacts").file(file).with(TestAssertion.as(userId, "USER"))
                            .header("Idempotency-Key", "upload-1"))
                    .andExpect(status().isCreated());
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_artifact", Integer.class)).isEqualTo(1);
            String conflictUpload = mvc.perform(multipart("/api/v1/artifacts").file(new MockMultipartFile("file", "other.txt", "text/plain", bytes))
                            .with(TestAssertion.as(userId, "USER")).header("Idempotency-Key", "upload-1"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(ApiResultJson.code(json, conflictUpload)).isEqualTo(ApiCode.CONFLICT.getCode());
            String badType = mvc.perform(multipart("/api/v1/artifacts").file(new MockMultipartFile("file", "bad.html", "text/html", bytes))
                            .with(TestAssertion.as(userId, "USER")).header("Idempotency-Key", "bad-type"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(ApiResultJson.code(json, badType)).isEqualTo(ApiCode.BAD_REQUEST.getCode());
            mvc.perform(multipart("/api/v1/artifacts").file(file).header("Idempotency-Key", "identity-missing"))
                    .andExpect(status().isUnauthorized());
            assertThat(mvc.perform(get("/api/v1/artifacts/{id}/content", artifactId).with(TestAssertion.as(userId, "USER")))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray()).isEqualTo(bytes);
            ArtifactVO corrupted = context.getBean(ArtifactService.class).upload(actor.userId(), "corrupt-check",
                    "corrupt.txt", "before".getBytes(StandardCharsets.UTF_8));
            jdbc.update("UPDATE sq_artifact SET content_bytes = ? WHERE artifact_id = ?",
                    "after".getBytes(StandardCharsets.UTF_8), corrupted.artifactId());
            String corruptedBody = mvc.perform(get("/api/v1/artifacts/{id}/content", corrupted.artifactId()).with(TestAssertion.as(userId, "USER")))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(ApiResultJson.code(json, corruptedBody)).isEqualTo(ApiCode.CONFLICT.getCode());
            Long otherUserId = IdWorker.getId();
            String foreignArtifact = mvc.perform(get("/api/v1/artifacts/{id}/content", artifactId).with(TestAssertion.as(otherUserId, "USER")))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(ApiResultJson.code(json, foreignArtifact)).isEqualTo(ApiCode.NOT_FOUND.getCode());
            String missingArtifact = mvc.perform(get("/api/v1/artifacts/{id}/content", IdWorker.getId()).with(TestAssertion.as(userId, "USER")))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(ApiResultJson.code(json, missingArtifact)).isEqualTo(ApiCode.NOT_FOUND.getCode());

            GoalTaskService tasks = context.getBean(GoalTaskService.class);
            Long conversation = tasks.createConversation(actor.userId(), "页面验收").conversationId();
            String invalidAttachment = mvc.perform(post("/api/v1/conversations/{id}/tasks", conversation).with(TestAssertion.as(userId, "USER"))
                            .header("Idempotency-Key", "invalid-attachment")
                            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                                    "message", "引用不存在的文件", "artifactIds", List.of(IdWorker.getId())))))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(ApiResultJson.code(json, invalidAttachment)).isEqualTo(ApiCode.NOT_FOUND.getCode());
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_goal_task", Integer.class)).isZero();
            String taskBody = mvc.perform(post("/api/v1/conversations/{id}/tasks", conversation).with(TestAssertion.as(userId, "USER"))
                            .header("Idempotency-Key", "task-1")
                            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                                    "message", "写一篇文章", "artifactIds", List.of(artifactId)))))
                    .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
            Long taskId = ApiResultJson.data(json, taskBody).path("taskId").asLong();
            jdbc.update("UPDATE sq_command SET status = 'CANCELLED' WHERE task_id = ?", taskId);
            assertThat(mvc.perform(get("/api/v1/tasks/{id}/artifacts", taskId).with(TestAssertion.as(userId, "USER")))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).contains(String.valueOf(artifactId));
            mvc.perform(get("/api/v1/tasks/{id}/jobs", taskId).with(TestAssertion.as(userId, "USER"))).andExpect(status().isOk());
            String foreignJobs = mvc.perform(get("/api/v1/tasks/{id}/jobs", taskId).with(TestAssertion.as(otherUserId, "USER")))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(ApiResultJson.code(json, foreignJobs)).isEqualTo(ApiCode.NOT_FOUND.getCode());
            Long binding = jdbc.queryForObject("SELECT binding_id FROM sq_runtime_binding WHERE task_id = ?", Long.class, taskId);
            Long operationId = IdWorker.getId();
            jdbc.update("""
                            INSERT INTO sq_operation (operation_id, user_id, task_id, binding_id, framework_turn_id,
                              tool_call_id, tool_key, tool_version, effect, request_hash, arguments_json, status, scope_id)
                            VALUES (?, ?, ?, ?, 'turn-1', 'call-1', 'mock_ticket_create', 'v1', 'WRITE', 'hash-1', ?, 'UNKNOWN', (SELECT scope_id FROM sq_runtime_binding WHERE binding_id = ?))
                            """, operationId, actor.userId(), taskId, binding,
                    "{\"title\":\"private-title\",\"password\":\"must-not-leak\"}", binding);
            String detail = mvc.perform(get("/api/v1/operations/{id}", operationId).with(TestAssertion.as(userId, "USER")))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(detail).doesNotContain("private-title", "must-not-leak", "frameworkTurnId");
            assertThat(ApiResultJson.data(json, detail).path("canReconcile").asBoolean()).isFalse();
            String reconcile = json.writeValueAsString(Map.of("expectedVersion", 0, "disposition", "UNRESOLVED",
                    "evidenceRefs", List.of(artifactId), "note", "仍需进一步确认"));
            String userReconcile = mvc.perform(post("/api/v1/operations/{id}/reconcile", operationId).with(TestAssertion.as(userId, "USER"))
                            .contentType(MediaType.APPLICATION_JSON).content(reconcile))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(ApiResultJson.code(json, userReconcile)).isEqualTo(ApiCode.FORBIDDEN.getCode());
            String operatorReconcile = mvc.perform(post("/api/v1/operations/{id}/reconcile", operationId).with(TestAssertion.as(actor.userId(), "OPERATOR"))
                            .contentType(MediaType.APPLICATION_JSON).content(reconcile))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(ApiResultJson.code(json, operatorReconcile)).isEqualTo(ApiCode.FORBIDDEN.getCode());
            jdbc.update("INSERT INTO sq_operation_reconciler (user_id, task_id, scope_id) SELECT user_id, task_id, scope_id FROM sq_goal_task WHERE user_id = ? AND task_id = ?", actor.userId(), taskId);
            mvc.perform(post("/api/v1/operations/{id}/reconcile", operationId).with(TestAssertion.as(actor.userId(), "OPERATOR"))
                            .contentType(MediaType.APPLICATION_JSON).content(reconcile))
                    .andExpect(status().isOk());
            assertThat(jdbc.queryForObject("SELECT status FROM sq_operation WHERE operation_id = ?", String.class, operationId)).isEqualTo("UNKNOWN");
            String repeatedReconcile = mvc.perform(post("/api/v1/operations/{id}/reconcile", operationId).with(TestAssertion.as(actor.userId(), "OPERATOR"))
                            .contentType(MediaType.APPLICATION_JSON).content(reconcile))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(ApiResultJson.code(json, repeatedReconcile)).isEqualTo(ApiCode.CONFLICT.getCode());

            Long interactionId = IdWorker.getId();
            String schema = context.getBean(InputFormSchemaService.class).normalize(json.readTree("""
                    {"type":"object","properties":{"style":{"type":"string","enum":["正式","轻松"]}},"required":["style"]}
                    """)).toString();
            context.getBean(InteractionMapper.class).insert(interactionId, actor.userId(), taskId, null,
                    binding, null, "FRAMEWORK", "input-1", "INPUT", "请选择文风", schema, 0,
                    "input-hash", 0, LocalDateTime.now().plusHours(1), actor.userId(), "tester");
            String response = json.writeValueAsString(Map.of("expectedVersion", 0,
                    "answer", Map.of("kind", "INPUT", "value", Map.of("style", "正式"))));
            String invalidAnswer = mvc.perform(post("/api/v1/interactions/{id}/responses", interactionId).with(TestAssertion.as(userId, "USER"))
                            .header("Idempotency-Key", "answer-1")
                            .contentType(MediaType.APPLICATION_JSON).content(response.replace("正式", "不允许")))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(ApiResultJson.code(json, invalidAnswer)).isEqualTo(ApiCode.BAD_REQUEST.getCode());
            for (int i = 0; i < 2; i++) {
                mvc.perform(post("/api/v1/interactions/{id}/responses", interactionId).with(TestAssertion.as(userId, "USER"))
                        .header("Idempotency-Key", "answer-1")
                        .contentType(MediaType.APPLICATION_JSON).content(response)).andExpect(status().isAccepted());
            }
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_command WHERE action = 'RESPOND'", Integer.class)).isEqualTo(1);
            var events = context.getBean(TaskEventMapper.class);
            events.insertProjected(actor.userId(), taskId, 2, "answer-old", "answer.completed",
                    "{\"text\":\"旧回答\",\"revision\":0}", LocalDateTime.now());
            events.insertProjected(actor.userId(), taskId, 3, "answer-new", "answer.replaced",
                    "{\"text\":\"新回答\",\"revision\":1}", LocalDateTime.now());
            jdbc.update("UPDATE sq_goal_task SET answer_revision = 1, event_seq = 3, status = 'PARTIAL' WHERE task_id = ?", taskId);
            String snapshot = mvc.perform(get("/api/v1/tasks/{id}/snapshot", taskId).with(TestAssertion.as(userId, "USER")))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(ApiResultJson.data(json, snapshot).path("answer").path("text").asText()).isEqualTo("新回答");
            assertThat(ApiResultJson.data(json, snapshot).path("answer").path("completed").asBoolean()).isFalse();
            assertThat(ApiResultJson.data(json, snapshot).path("task").path("status").asText()).isEqualTo("PARTIAL");
            jdbc.update("UPDATE sq_conversation SET status = 'DELETING' WHERE conversation_id = ?", conversation);
            String deletedContent = mvc.perform(get("/api/v1/artifacts/{id}/content", artifactId).with(TestAssertion.as(userId, "USER")))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(ApiResultJson.code(json, deletedContent)).isEqualTo(ApiCode.NOT_FOUND.getCode());
        }
    }
}
