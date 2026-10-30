package com.agentsquad.squad.runtime;

import com.agentsquad.squad.AgentSquadApplication;
import com.agentsquad.squad.artifact.service.ArtifactDeliveryService;
import com.agentsquad.squad.conversation.dto.MessageCreateRequest;
import com.agentsquad.squad.conversation.service.MessageAcceptanceService;
import com.agentsquad.squad.conversation.service.ResponseContextService;
import com.agentsquad.squad.integration.agentscope.NativeEventView;
import com.agentsquad.squad.integration.agentscope.NativeProjectionService;
import com.agentsquad.squad.memory.dto.MemoryCreate;
import com.agentsquad.squad.memory.dto.MemoryWriteRequest;
import com.agentsquad.squad.memory.service.ExplicitMemoryWriteService;
import com.agentsquad.squad.memory.service.MemoryService;
import com.agentsquad.squad.runtime.domain.MasterAgentIdentity;
import com.agentsquad.squad.runtime.domain.ProjectionTarget;
import com.agentsquad.squad.runtime.service.ExecutionAuthorizationService;
import com.agentsquad.squad.runtime.service.ExecutionRestrictionService;
import com.agentsquad.squad.sandbox.dto.SandboxDriverResult;
import com.agentsquad.squad.sandbox.dto.SandboxExecuteRequest;
import com.agentsquad.squad.sandbox.dto.SandboxOutput;
import com.agentsquad.squad.sandbox.service.SandboxAccessService;
import com.agentsquad.squad.sandbox.service.SandboxRunTransaction;
import com.agentsquad.squad.skill.dto.SkillFile;
import com.agentsquad.squad.skill.dto.SkillGrantRequest;
import com.agentsquad.squad.skill.dto.SkillImportRequest;
import com.agentsquad.squad.skill.service.SkillService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 知识治理与运行/交付的交叉验收。
 * <p>
 * 覆盖正式SDK按需加载、记忆删除后的迟到正文/产物拒绝，以及后续会话材料不重新引入已失效来源。
 */
class ExecutionKnowledgeIntegrationTest {
    private static final String USER = "10000000-0000-0000-0000-000000000001";

    @Test
    void ordinaryReplyDiscoversAndLoadsSkillThroughSdk(@TempDir Path directory) throws Exception {
        try (D4SkillModelStub model = new D4SkillModelStub(); var context = start(directory, model, true)) {
            var skills = context.getBean(SkillService.class);
            var skill = skills.importPackage(USER, new SkillImportRequest(List.of(new SkillFile("SKILL.md",
                    "---\nname: tone\ndescription: 适用于回复语气的规则\nversion: 1.0.0\n---\nSKILL_BODY_TOKEN 使用简洁措辞。"))));
            skills.changeGrant(USER, skill.skillVersionId(), new SkillGrantRequest(true, 0L));
            context.getBean(MemoryService.class).create(USER, new MemoryCreate("tone", "MEMORY_VALUE_TOKEN 喜欢短句", null));
            var receipt = context.getBean(MessageAcceptanceService.class).accept(USER, null, "knowledge",
                    new MessageCreateRequest("请按我的偏好回复", List.of()));
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            String status = null;
            for (int i = 0; i < 300; i++) {
                status = jdbc.queryForObject("SELECT status FROM sq_response_execution WHERE execution_id=?", String.class, receipt.executionId());
                if (List.of("SUCCEEDED", "FAILED").contains(status)) {
                    break;
                }
                Thread.sleep(100);
            }
            if (!"SUCCEEDED".equals(status)) {
                String binding = jdbc.queryForObject("SELECT binding_id FROM sq_response_execution WHERE execution_id=?", String.class, receipt.executionId());
                String session = jdbc.queryForObject("SELECT framework_session_id FROM sq_runtime_binding WHERE binding_id=?", String.class, binding);
                var target = new ProjectionTarget(USER, null, binding, MasterAgentIdentity.AGENT_ID, session, 0);
                var diagnostic = context.getBean(com.agentsquad.squad.integration.agentscope.NativeSessionGateway.class)
                        .readEvents(target, 0, 100).stream().filter(event -> event.type().contains("error") || "run/end".equals(event.type())).toList();
                var master = (io.agentscope.harness.agent.HarnessAgent) org.springframework.test.util.ReflectionTestUtils.getField(
                        context.getBean(com.agentsquad.squad.integration.agentscope.NativeSessionGateway.class), "master");
                String error = master.session(io.agentscope.core.agent.RuntimeContext.builder().userId(USER).sessionId(session).build())
                        .task(receipt.commandId()).error();
                assertThat(status).as("SDK error=%s; events=%s; modelCalls=%s", error, diagnostic, model.calls.get()).isEqualTo("SUCCEEDED");
            }
            assertThat(model.calls.get()).isEqualTo(3);
            assertThat(model.requests.getFirst()).contains("tone_platform-skill:", "MEMORY_VALUE_TOKEN").doesNotContain("SKILL_BODY_TOKEN");
            assertThat(model.requests.get(1)).contains("SKILL_BODY_TOKEN");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_goal_task", Long.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_budget_reservation WHERE status='SETTLED'", Long.class)).isEqualTo(3);
        }
    }

    @Test
    void deletedMemoryBlocksLateAnswerAndArtifactAndCannotReturnFromHistory(@TempDir Path directory) throws Exception {
        try (D4SkillModelStub model = new D4SkillModelStub(); var context = start(directory, model, false)) {
            var messages = context.getBean(MessageAcceptanceService.class);
            var receipt = messages.accept(USER, null, "source", new MessageCreateRequest("请记住代号SECRET_LINEAGE_TOKEN", List.of()));
            var jdbc = context.getBean(JdbcTemplate.class);
            String binding = jdbc.queryForObject("SELECT binding_id FROM sq_response_execution WHERE execution_id=?", String.class, receipt.executionId());
            String scope = jdbc.queryForObject("SELECT scope_id FROM sq_response_execution WHERE execution_id=?", String.class, receipt.executionId());
            String session = jdbc.queryForObject("SELECT framework_session_id FROM sq_runtime_binding WHERE binding_id=?", String.class, binding);
            var writes = context.getBean(ExplicitMemoryWriteService.class);
            var request = new MemoryWriteRequest("remember", "CREATE", "code", "SECRET_LINEAGE_TOKEN", null, null, "请记住代号SECRET_LINEAGE_TOKEN");
            var memory = writes.write(USER, session, request);
            context.getBean(ExecutionAuthorizationService.class).prepare(USER, scope);
            var identity = context.getBean(SandboxAccessService.class).resolve(USER, session, null, "turn", "tool");
            var ledger = context.getBean(SandboxRunTransaction.class);
            var runRequest = new SandboxExecuteRequest("file", "print('file')", List.of(), List.of("result.txt"), 5);
            var claim = ledger.begin(identity, runRequest, "a".repeat(64));
            var result = ledger.finish(identity, claim.run().executionId(), runRequest,
                    new SandboxDriverResult(0, "", "", false, List.of(new SandboxOutput("result.txt", "SECRET_LINEAGE_TOKEN".getBytes(StandardCharsets.UTF_8)))));
            var target = new ProjectionTarget(USER, null, binding, MasterAgentIdentity.AGENT_ID, session, 0);
            var projector = context.getBean(NativeProjectionService.class);
            projector.accept(target, new NativeEventView(1, "start", "run/start", "run", "turn", null, 0, Map.of()));
            context.getBean(MemoryService.class).delete(USER, memory.memoryId(), 0);
            assertThatThrownBy(() -> projector.accept(target,
                    new NativeEventView(2, "end", "run/end", "run", "turn", "completed", 0, Map.of())))
                    .hasMessageContaining("旧上下文");
            var stage = result.stagedArtifacts().getFirst();
            assertThatThrownBy(() -> context.getBean(ArtifactDeliveryService.class).commit(USER, session, null, stage.stageId(), stage.sha256()))
                    .hasMessageContaining("旧上下文");
            context.getBean(ExecutionRestrictionService.class).restrict(USER, scope, binding);
            var next = messages.accept(USER, receipt.conversationId(), "next", new MessageCreateRequest("继续说说其他事", List.of()));
            String nextBinding = jdbc.queryForObject("SELECT binding_id FROM sq_response_execution WHERE execution_id=?", String.class, next.executionId());
            var history = context.getBean(ResponseContextService.class).freeze(USER, nextBinding);
            assertThat(history.messages().toString()).doesNotContain("SECRET_LINEAGE_TOKEN");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_artifact", Long.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_message WHERE role='ASSISTANT'", Long.class)).isZero();
            assertThat(model.calls.get()).isZero();
        }
    }

    private ConfigurableApplicationContext start(Path directory, D4SkillModelStub model, boolean fast) {
        return SpringApplication.run(AgentSquadApplication.class, "--spring.main.web-application-type=none",
                "--spring.datasource.url=" + com.agentsquad.squad.testsupport.MySqlTestDatabase.url(directory.resolve("knowledge")),
                "--spring.datasource.username=" + com.agentsquad.squad.testsupport.MySqlTestDatabase.username(), "--spring.datasource.password=" + com.agentsquad.squad.testsupport.MySqlTestDatabase.password(), "--spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
                "--agentsquad.delivery.enabled=true", "--agentsquad.delivery.poll-delay-ms=" + (fast ? 100 : 3600000),
                "--agentsquad.projection.poll-delay-ms=" + (fast ? 100 : 3600000), "--agentsquad.model.api-key=test-only",
                "--agentsquad.model.base-url=" + model.baseUrl(), "--agentsquad.model.name=test-model",
                "--agentsquad.worker.model-profiles.default.api-key=test-only",
                "--agentsquad.worker.model-profiles.default.base-url=" + model.baseUrl(),
                "--agentsquad.worker.model-profiles.default.model-name=test-model");
    }
}
