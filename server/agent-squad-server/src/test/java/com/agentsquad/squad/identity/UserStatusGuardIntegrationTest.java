package com.agentsquad.squad.identity;

import com.agentsquad.squad.AgentSquadApplication;
import com.agentsquad.squad.common.exception.RuntimeBusinessException;
import com.agentsquad.squad.common.exception.RuntimeExceptionCode;
import com.agentsquad.squad.domain.request.MessageCreateRequest;
import com.agentsquad.squad.domain.response.MessageReceiptResponse;
import com.agentsquad.squad.conversation.service.CommandClaimService;
import com.agentsquad.squad.conversation.service.MessageAcceptanceService;
import com.agentsquad.squad.testsupport.MySqlTestDatabase;
import com.agentsquad.squad.testsupport.TestUserDirectory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 账号停用与执行领取的真实数据库竞态。
 *
 * <p>停用后新的消息受理与命令领取必须被拒绝；已领取的 DELIVERING 行保持原租约，不被停用动作改写或取消。</p>
 */
class UserStatusGuardIntegrationTest {

    @Test
    void disabledUserStopsNewWorkWithoutRewritingInFlightLedger(@TempDir Path directory) {
        String owner = UUID.randomUUID().toString();
        try (ConfigurableApplicationContext context = start(MySqlTestDatabase.url(directory.resolve("status-guard")),
                owner)) {
            MessageAcceptanceService messages = context.getBean(MessageAcceptanceService.class);
            CommandClaimService claims = context.getBean(CommandClaimService.class);
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);

            MessageReceiptResponse first = messages.accept(owner, null, "first",
                    new MessageCreateRequest("第一条消息", List.of()));
            assertThat(count(jdbc, "sq_conversation", owner)).isEqualTo(1);

            TestUserDirectory.disable(context, owner);
            assertForbidden(() -> messages.accept(owner, null, "after-disable",
                    new MessageCreateRequest("停用后不能受理", List.of())));
            assertThat(count(jdbc, "sq_conversation", owner)).isEqualTo(1);

            LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
            String firstToken = UUID.randomUUID().toString();
            assertForbidden(() -> claims.claim(first.commandId(), firstToken, now, now.plusSeconds(30)));
            assertCommand(jdbc, first.commandId(), "PENDING", null);

            TestUserDirectory.activate(context, owner);
            assertThat(claims.claim(first.commandId(), firstToken, now, now.plusSeconds(30))).isTrue();
            assertCommand(jdbc, first.commandId(), "DELIVERING", firstToken);

            TestUserDirectory.disable(context, owner);
            assertForbidden(() -> claims.claim(first.commandId(), UUID.randomUUID().toString(),
                    now.plusSeconds(1), now.plusSeconds(31)));
            assertCommand(jdbc, first.commandId(), "DELIVERING", firstToken);
        }
    }

    private static ConfigurableApplicationContext start(String url, String owner) {
        ConfigurableApplicationContext context = SpringApplication.run(AgentSquadApplication.class, "--server.port=0",
                "--spring.datasource.url=" + url,
                "--spring.datasource.username=" + MySqlTestDatabase.username(),
                "--spring.datasource.password=" + MySqlTestDatabase.password(),
                "--spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
                "--agentsquad.delivery.enabled=false",
                "--agentsquad.auth.user-id=" + owner);
        TestUserDirectory.activate(context, owner);
        return context;
    }

    private static void assertForbidden(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(RuntimeBusinessException.class,
                        error -> assertThat(error.code()).isEqualTo(RuntimeExceptionCode.FORBIDDEN));
    }

    private static void assertCommand(JdbcTemplate jdbc, String commandId, String status, String token) {
        assertThat(jdbc.queryForObject("SELECT status FROM sq_command WHERE command_id = ?",
                String.class, commandId)).isEqualTo(status);
        assertThat(jdbc.queryForObject("SELECT delivery_token FROM sq_command WHERE command_id = ?",
                String.class, commandId)).isEqualTo(token);
    }

    private static long count(JdbcTemplate jdbc, String table, String userId) {
        Long value = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE user_id = ?",
                Long.class, userId);
        return value == null ? 0 : value;
    }
}
