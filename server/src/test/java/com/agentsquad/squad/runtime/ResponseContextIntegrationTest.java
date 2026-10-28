package com.agentsquad.squad.runtime;

import com.agentsquad.squad.artifact.ArtifactService;
import com.agentsquad.squad.common.exception.RuntimeBusinessException;
import com.agentsquad.squad.conversation.domain.ResponseContextSnapshot;
import com.agentsquad.squad.conversation.dto.MessageReceipt;
import com.agentsquad.squad.conversation.service.ResponseContextService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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
 * 固定会话前缀持久验证。
 * <p>
 * 验证追问引用、未来消息隔离、重启重投与代际/附件屏障；不以材料构建替代SDK执行验收。
 */
class ResponseContextIntegrationTest {

    @Test
    void freezesCompletedPrefixAcrossRestartAndExcludesFutureMessages(@TempDir Path directory) throws Exception {
        String url = url(directory);
        String owner = UUID.randomUUID().toString();
        String binding;
        ResponseContextSnapshot frozen;
        try (ConfigurableApplicationContext context = MessageAcceptanceHttpTest.start(url, "sa", "", owner)) {
            MessageTestActor actor = MessageAcceptanceHttpTest.actor(context);
            MessageReceipt first = MessageAcceptanceHttpTest.send(context, actor, "/api/v1/conversations/messages",
                    "first", Map.of("text", "主角叫小明"));
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            jdbc.update("UPDATE sq_conversation SET message_seq = 2 WHERE conversation_id = ?", first.conversationId());
            jdbc.update("INSERT INTO sq_message (message_id,user_id,conversation_id,message_seq,role,content,completed,occurred_at) VALUES (?,?,?,2,'ASSISTANT','记住了，小明。',TRUE,CURRENT_TIMESTAMP)",
                    UUID.randomUUID().toString(), owner, first.conversationId());
            MessageReceipt follow = MessageAcceptanceHttpTest.send(context, actor,
                    "/api/v1/conversations/" + first.conversationId() + "/messages", "follow", Map.of("text", "他叫什么？"));
            MessageAcceptanceHttpTest.send(context, actor, "/api/v1/conversations/" + first.conversationId() + "/messages",
                    "future", Map.of("text", "未来消息不应进入前一个回复"));
            binding = binding(jdbc, follow);
            frozen = context.getBean(ResponseContextService.class).freeze(owner, binding);
            assertThat(frozen.throughSequence()).isEqualTo(3);
            assertThat(frozen.messages()).extracting(m -> m.text()).containsExactly("主角叫小明", "记住了，小明。", "他叫什么？");
            jdbc.update("UPDATE sq_message SET content = '改变后的正文', revision = revision + 1 WHERE message_id = ?", first.messageId());
            assertThat(context.getBean(ResponseContextService.class).freeze(owner, binding)).isEqualTo(frozen);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_goal_task", Long.class)).isZero();
        }
        try (ConfigurableApplicationContext context = MessageAcceptanceHttpTest.start(url, "sa", "", owner)) {
            assertThat(context.getBean(ResponseContextService.class).freeze(owner, binding)).isEqualTo(frozen);
            assertThatThrownBy(() -> context.getBean(ResponseContextService.class).freeze(UUID.randomUUID().toString(), binding))
                    .isInstanceOf(RuntimeBusinessException.class);
            context.getBean(JdbcTemplate.class).update("UPDATE sq_conversation SET generation = generation + 1 WHERE user_id = ?", owner);
            assertThatThrownBy(() -> context.getBean(ResponseContextService.class).freeze(owner, binding))
                    .isInstanceOf(RuntimeBusinessException.class);
        }
    }

    @Test
    void expiredMaterialBlocksEvenFrozenContext(@TempDir Path directory) throws Exception {
        String owner = UUID.randomUUID().toString();
        try (ConfigurableApplicationContext context = MessageAcceptanceHttpTest.start(url(directory), "sa", "", owner)) {
            String file = context.getBean(ArtifactService.class).upload(owner, "input", "facts.txt",
                    "材料".getBytes(StandardCharsets.UTF_8)).artifactId();
            MessageReceipt receipt = MessageAcceptanceHttpTest.send(context, MessageAcceptanceHttpTest.actor(context),
                    "/api/v1/conversations/messages", "file", Map.of("text", "分析材料", "artifactIds", List.of(file)));
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            String binding = binding(jdbc, receipt);
            ResponseContextService service = context.getBean(ResponseContextService.class);
            assertThat(service.freeze(owner, binding).messages().getFirst().artifactIds()).containsExactly(file);
            jdbc.update("UPDATE sq_artifact SET expires_at = '2000-01-01' WHERE artifact_id = ?", file);
            assertThatThrownBy(() -> service.freeze(owner, binding)).isInstanceOf(RuntimeBusinessException.class);
        }
    }

    @Test
    void overLimitFailsWithoutSilentlyDroppingHistoryOrSavingSnapshot(@TempDir Path directory) throws Exception {
        String owner = UUID.randomUUID().toString();
        try (ConfigurableApplicationContext context = MessageAcceptanceHttpTest.start(url(directory), "sa", "", owner)) {
            MessageReceipt receipt = MessageAcceptanceHttpTest.send(context, MessageAcceptanceHttpTest.actor(context),
                    "/api/v1/conversations/messages", "large", Map.of("text", "开始"));
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            jdbc.update("UPDATE sq_message SET content = ? WHERE message_id = ?", "文".repeat(200001), receipt.messageId());
            assertThatThrownBy(() -> context.getBean(ResponseContextService.class).freeze(owner, binding(jdbc, receipt)))
                    .isInstanceOf(RuntimeBusinessException.class).hasMessageContaining("超过当前可处理范围");
            assertThat(jdbc.queryForObject("SELECT context_json FROM sq_response_execution WHERE execution_id = ?",
                    String.class, receipt.executionId())).isNull();
        }
    }

    private String binding(JdbcTemplate jdbc, MessageReceipt receipt) {
        return jdbc.queryForObject("SELECT binding_id FROM sq_response_execution WHERE execution_id = ?", String.class, receipt.executionId());
    }

    private String url(Path directory) {
        return "jdbc:h2:file:" + directory.resolve("context") + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;LOCK_TIMEOUT=10000";
    }
}
