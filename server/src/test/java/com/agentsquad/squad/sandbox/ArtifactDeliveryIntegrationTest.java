package com.agentsquad.squad.sandbox;

import com.agentsquad.squad.AgentSquadApplication;
import com.agentsquad.squad.artifact.ArtifactService;
import com.agentsquad.squad.artifact.service.ArtifactDeliveryService;
import com.agentsquad.squad.artifact.service.ArtifactRetentionService;
import com.agentsquad.squad.conversation.dto.MessageCreateRequest;
import com.agentsquad.squad.conversation.service.MessageAcceptanceService;
import com.agentsquad.squad.runtime.service.ExecutionAuthorizationService;
import com.agentsquad.squad.sandbox.dto.SandboxDriverResult;
import com.agentsquad.squad.sandbox.dto.SandboxExecuteRequest;
import com.agentsquad.squad.sandbox.dto.SandboxOutput;
import com.agentsquad.squad.sandbox.service.SandboxAccessService;
import com.agentsquad.squad.sandbox.service.SandboxRunTransaction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 沙箱账本与产物发布的正式服务集成验证。
 * <p>
 * 验证派发事务、失联不重放、暂存不可下载、原子幂等提交和保留清理；不代替实际容器隔离实验。
 */
class ArtifactDeliveryIntegrationTest {
    @Test
    void durableClaimStageCommitAndRetention(@TempDir Path directory) {
        String userId = UUID.randomUUID().toString();
        try (ConfigurableApplicationContext context = start(directory, userId)) {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            var receipt = context.getBean(MessageAcceptanceService.class).accept(userId, null, "sandbox-first",
                    new MessageCreateRequest("生成文本附件", List.of()));
            String scopeId = jdbc.queryForObject("SELECT scope_id FROM sq_response_execution WHERE execution_id = ?",
                    String.class, receipt.executionId());
            String sessionId = jdbc.queryForObject("SELECT framework_session_id FROM sq_runtime_binding WHERE scope_id = ?",
                    String.class, scopeId);
            context.getBean(ExecutionAuthorizationService.class).prepare(userId, scopeId);
            var identity = context.getBean(SandboxAccessService.class).resolve(userId, sessionId, null, "turn", "call");
            var request = new SandboxExecuteRequest("report", "print('report')", List.of(), List.of("result.txt"), 5);
            SandboxRunTransaction ledger = context.getBean(SandboxRunTransaction.class);
            var claim = ledger.begin(identity, request, "a".repeat(64));
            assertThat(claim.newlyAccepted()).isTrue();
            var repeat = ledger.begin(identity, request, "a".repeat(64));
            assertThat(repeat.newlyAccepted()).isFalse();
            assertThatThrownBy(() -> ledger.replay(repeat.run())).hasMessageContaining("不能自动重放");
            assertThatThrownBy(() -> ledger.begin(identity, request, "b".repeat(64))).hasMessageContaining("输入不一致");
            byte[] content = "已验证的交付".getBytes(StandardCharsets.UTF_8);
            var result = ledger.finish(identity, claim.run().executionId(), request,
                    new SandboxDriverResult(0, "report", "", false, List.of(new SandboxOutput("result.txt", content))));
            var stage = result.stagedArtifacts().getFirst();
            ArtifactService artifacts = context.getBean(ArtifactService.class);
            assertThatThrownBy(() -> artifacts.content(userId, stage.stageId())).hasMessageContaining("不存在");
            ArtifactDeliveryService delivery = context.getBean(ArtifactDeliveryService.class);
            assertThatThrownBy(() -> delivery.commit(UUID.randomUUID().toString(), sessionId, null, stage.stageId(), stage.sha256()))
                    .hasMessageContaining("不存在");
            assertThatThrownBy(() -> delivery.commit(userId, sessionId, null, stage.stageId(), "c".repeat(64)))
                    .hasMessageContaining("摘要");
            var published = delivery.commit(userId, sessionId, null, stage.stageId(), stage.sha256());
            assertThat(delivery.commit(userId, sessionId, null, stage.stageId(), stage.sha256()).artifactId())
                    .isEqualTo(published.artifactId());
            assertThat(artifacts.content(userId, published.artifactId()).content()).isEqualTo(content);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_scope_artifact WHERE scope_id = ?", Integer.class, scopeId))
                    .isEqualTo(1);
            jdbc.update("UPDATE sq_artifact SET expires_at = DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 1 DAY) WHERE artifact_id = ?",
                    published.artifactId());
            assertThat(context.getBean(ArtifactRetentionService.class).purgeExpired()).isEqualTo(1);
            assertThatThrownBy(() -> artifacts.content(userId, published.artifactId())).hasMessageContaining("不存在");
            assertThat(jdbc.queryForObject("SELECT OCTET_LENGTH(content_bytes) FROM sq_artifact WHERE artifact_id = ?",
                    Integer.class, published.artifactId())).isZero();
            assertThat(jdbc.queryForObject("SELECT del_flag FROM sq_scope_artifact WHERE artifact_id = ?",
                    String.class, published.artifactId())).isEqualTo("1");
        }
    }

    private ConfigurableApplicationContext start(Path directory, String userId) {
        return SpringApplication.run(AgentSquadApplication.class, "--server.port=0",
                "--spring.datasource.url=" + com.agentsquad.squad.testsupport.MySqlTestDatabase.url(directory.resolve("sandbox")),
                "--spring.datasource.username=" + com.agentsquad.squad.testsupport.MySqlTestDatabase.username(), "--spring.datasource.password=" + com.agentsquad.squad.testsupport.MySqlTestDatabase.password(),
                "--spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver", "--agentsquad.delivery.enabled=false",
                "--agentsquad.auth.user-id=" + userId, "--agentsquad.auth.username=tester",
                "--agentsquad.auth.password=test-only");
    }
}
