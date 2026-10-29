package com.agentsquad.squad.runtime;

import com.agentsquad.squad.common.exception.RuntimeBusinessException;
import com.agentsquad.squad.conversation.dto.ConversationTitleUpdate;
import com.agentsquad.squad.conversation.dto.MessageCreateRequest;
import com.agentsquad.squad.conversation.service.MessageAcceptanceService;
import com.agentsquad.squad.conversation.service.ConversationTitleService;
import com.agentsquad.squad.presentation.service.ConversationQueryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 会话一致前缀、历史分页与主题并发验证。
 * <p>
 * 从正式消息入口建立多页历史，读取和续订不得创建新命令；跨归属、未来或过期水位必须拒绝。
 */
class ConversationReadIntegrationTest {
    @Test
    void pagesHistoryAndRejectsInvalidEventCursors(@TempDir Path directory) {
        try (var context = start(directory)) {
            verifyHistory(context);
        }
    }

    @Test
    void manualTitleUsesIndependentVersionAndStableListCursor(@TempDir Path directory) {
        try (var context = start(directory)) {
            verifyTitles(context);
        }
    }

    static void verifyHistory(ConfigurableApplicationContext context) {
        String user = UUID.randomUUID().toString();
        var messages = context.getBean(MessageAcceptanceService.class);
        var first = messages.accept(user, null, "first", new MessageCreateRequest("第一条消息", List.of()));
        for (int i = 2; i <= 43; i++) {
            messages.accept(user, first.conversationId(), "message-" + i, new MessageCreateRequest("消息" + i, List.of()));
        }
        var reads = context.getBean(ConversationQueryService.class);
        var page = reads.snapshot(user, first.conversationId(), null, 20);
        assertThat(page.messages()).hasSize(20);
        assertThat(page.messages().getFirst().sequence()).isEqualTo(24);
        assertThat(page.messages().getLast().sequence()).isEqualTo(43);
        assertThat(page.nextBeforeSeq()).isEqualTo(24);
        var older = reads.snapshot(user, first.conversationId(), page.nextBeforeSeq(), 20);
        assertThat(older.messages().getFirst().sequence()).isEqualTo(4);
        var oldest = reads.snapshot(user, first.conversationId(), older.nextBeforeSeq(), 20);
        assertThat(oldest.messages()).hasSize(3);
        assertThat(oldest.messages().getFirst().text()).isEqualTo("第一条消息");
        assertThat(oldest.nextBeforeSeq()).isNull();
        assertThat(reads.events(user, first.conversationId(), page.cursor())).isEmpty();
        assertThat(reads.events(user, first.conversationId(), first.cursor())).hasSize(84);
        assertThatThrownBy(() -> reads.snapshot(UUID.randomUUID().toString(), first.conversationId(), null, 20)).isInstanceOf(RuntimeBusinessException.class);
        assertThatThrownBy(() -> reads.events(user, first.conversationId(), UUID.randomUUID() + ":0:0")).isInstanceOf(RuntimeBusinessException.class);
        assertThatThrownBy(() -> reads.events(user, first.conversationId(), first.conversationId() + ":0:10000")).isInstanceOf(RuntimeBusinessException.class);
        assertThatThrownBy(() -> reads.events(user, first.conversationId(), first.conversationId() + ":1:0")).isInstanceOf(RuntimeBusinessException.class);
        JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_command WHERE user_id=?", Long.class, user)).isEqualTo(43);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_goal_task WHERE user_id=?", Long.class, user)).isZero();
        jdbc.update("DELETE FROM sq_conversation_event WHERE user_id=? AND event_seq<4", user);
        assertThatThrownBy(() -> reads.events(user, first.conversationId(), first.cursor())).isInstanceOf(RuntimeBusinessException.class).hasMessageContaining("保留范围");
        jdbc.update("UPDATE sq_conversation SET status='DELETING',generation=generation+1 WHERE user_id=?", user);
        assertThatThrownBy(() -> reads.snapshot(user, first.conversationId(), null, 20)).isInstanceOf(RuntimeBusinessException.class);
        assertThatThrownBy(() -> reads.events(user, first.conversationId(), page.cursor())).isInstanceOf(RuntimeBusinessException.class);
    }

    static void verifyTitles(ConfigurableApplicationContext context) {
        String user = UUID.randomUUID().toString();
        var messages = context.getBean(MessageAcceptanceService.class);
        var first = messages.accept(user, null, "first", new MessageCreateRequest("\u0001  讨论一下   合作方案\n", List.of()));
        var second = messages.accept(user, null, "second", new MessageCreateRequest("第二个主题", List.of()));
        var service = context.getBean(ConversationTitleService.class);
        var page = service.list(user, null, 1);
        assertThat(page.items()).hasSize(1);
        assertThat(page.items().getFirst().conversationId()).isEqualTo(second.conversationId());
        assertThat(page.nextCursor()).isNotBlank();
        var older = service.list(user, page.nextCursor(), 1);
        assertThat(older.items().getFirst().title()).isEqualTo("讨论一下 合作方案");
        assertThat(older.nextCursor()).isNull();
        assertThatThrownBy(() -> service.list(UUID.randomUUID().toString(), page.nextCursor(), 1)).isInstanceOf(RuntimeBusinessException.class);
        var renamed = service.rename(user, first.conversationId(), new ConversationTitleUpdate(" 我的\n 合作主题 ", 0L));
        assertThat(renamed.title()).isEqualTo("我的 合作主题");
        assertThat(renamed.titleSource()).isEqualTo("MANUAL");
        assertThatThrownBy(() -> service.rename(user, first.conversationId(), new ConversationTitleUpdate("旧版本名称", 0L))).isInstanceOf(RuntimeBusinessException.class);
        assertThatThrownBy(() -> service.rename(user, first.conversationId(), new ConversationTitleUpdate("\u200b\u0001", 1L))).isInstanceOf(RuntimeBusinessException.class);
        assertThatThrownBy(() -> service.rename(UUID.randomUUID().toString(), first.conversationId(), new ConversationTitleUpdate("其他人名称", 1L))).isInstanceOf(RuntimeBusinessException.class);
        assertThat(context.getBean(ConversationQueryService.class).snapshot(user, first.conversationId(), null, 20).conversation().title()).isEqualTo("我的 合作主题");
    }

    private ConfigurableApplicationContext start(Path directory) {
        return MessageAcceptanceHttpTest.start("jdbc:h2:file:" + directory.resolve("read") + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;LOCK_TIMEOUT=10000", "sa", "", UUID.randomUUID().toString());
    }
}
