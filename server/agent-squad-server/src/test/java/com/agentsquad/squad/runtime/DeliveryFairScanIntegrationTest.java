package com.agentsquad.squad.runtime;

import com.agentsquad.squad.AgentSquadApplication;
import com.agentsquad.squad.mapper.DeliveryMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 到期命令公平扫描验证。
 *
 * 使用真实 MySQL 与正式 Mapper SQL，检查按用户轮次选取、到期与租约过滤、
 * 禁用账号排除及同用户稳定顺序；不调用模型或外部服务。
 */
class DeliveryFairScanIntegrationTest {

    private static final LocalDateTime BASE = LocalDateTime.of(2026, 1, 1, 0, 0);
    private static final LocalDateTime NOW = BASE.plusDays(1);

    @Test
    void controlScanFindsStopOutsideOrdinaryWindow(@TempDir Path directory) {
        try (ConfigurableApplicationContext context = start(directory)) {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            for (int index = 0; index < 30; index++) {
                command(jdbc, UUID.randomUUID().toString(), "PENDING", BASE, null);
            }
            String stop = command(jdbc, UUID.randomUUID().toString(), "PENDING", BASE.plusHours(1), null);
            jdbc.update("UPDATE sq_command SET action = 'STOP' WHERE command_id = ?", stop);
            DeliveryMapper mapper = context.getBean(DeliveryMapper.class);
            assertThat(mapper.findDueIds(NOW)).doesNotContain(stop);
            assertThat(mapper.findDueControlIds(NOW)).containsExactly(stop);
        }
    }

    @Test
    void runtimeSlotsEnforceGlobalCapacityAndReleaseIdempotently(@TempDir Path directory) {
        try (ConfigurableApplicationContext context = start(directory)) {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            var admission = context.getBean(com.agentsquad.squad.service.RuntimeAdmissionService.class);
            org.springframework.test.util.ReflectionTestUtils.setField(admission, "enabled", true);
            org.springframework.test.util.ReflectionTestUtils.setField(admission, "globalLimit", 1);
            org.springframework.test.util.ReflectionTestUtils.setField(admission, "perUserLimit", 1);
            var transaction = new org.springframework.transaction.support.TransactionTemplate(
                    context.getBean(org.springframework.transaction.PlatformTransactionManager.class));
            String userA = UUID.randomUUID().toString();
            String userB = UUID.randomUUID().toString();
            String bindingA = UUID.randomUUID().toString();
            String bindingB = UUID.randomUUID().toString();
            for (String[] pair : List.of(new String[]{userA, bindingA}, new String[]{userB, bindingB})) {
                jdbc.update("""
                        INSERT INTO sq_runtime_binding
                            (binding_id, user_id, role, agent_id, framework_session_id, status)
                        VALUES (?, ?, 'MASTER', 'master', ?, 'ACTIVE')
                        """, pair[1], pair[0], UUID.randomUUID().toString());
            }
            String commandA = UUID.randomUUID().toString();
            String commandB = UUID.randomUUID().toString();
            transaction.executeWithoutResult(status -> admission.acquire(userA, bindingA, commandA, "SUBMIT"));
            transaction.executeWithoutResult(status -> admission.acquire(userA, bindingA, commandA, "SUBMIT"));
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> transaction.executeWithoutResult(
                    status -> admission.acquire(userB, bindingB, commandB, "SUBMIT")))
                    .isInstanceOf(com.agentsquad.squad.common.exception.RuntimeAdmissionBusyException.class);
            admission.releaseUnsubmitted(userA, bindingA, "stale-command");
            assertThat(jdbc.queryForObject("SELECT active_count FROM sq_runtime_admission_counter WHERE pool_key = 'GLOBAL'",
                    Integer.class)).isEqualTo(1);
            admission.releaseUnsubmitted(userA, bindingA, commandA);
            admission.releaseUnsubmitted(userA, bindingA, commandA);
            assertThat(jdbc.queryForObject("SELECT active_count FROM sq_runtime_admission_counter WHERE pool_key = 'GLOBAL'",
                    Integer.class)).isZero();
            transaction.executeWithoutResult(status -> admission.acquire(userB, bindingB, commandB, "SUBMIT"));
        }
    }

    @Test
    void invalidCommandIsBlockedOnlyByCurrentDeliveryOwner(@TempDir Path directory) {
        try (ConfigurableApplicationContext context = start(directory)) {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            String user = UUID.randomUUID().toString();
            String id = command(jdbc, user, "DELIVERING", BASE, NOW.plusHours(1));
            jdbc.update("UPDATE sq_command SET delivery_token = ? WHERE command_id = ?", "owner", id);
            DeliveryMapper mapper = context.getBean(DeliveryMapper.class);

            assertThat(mapper.blockInvalid(id, "stale-owner", "UNKNOWN_ACTION")).isZero();
            assertThat(mapper.blockInvalid(id, "owner", "UNKNOWN_ACTION")).isEqualTo(1);
            assertThat(mapper.blockInvalid(id, "owner", "UNKNOWN_ACTION")).isZero();
            assertThat(mapper.findDueIds(NOW.plusDays(1))).doesNotContain(id);
            assertThat(jdbc.queryForObject("SELECT last_error_code FROM sq_command WHERE command_id = ?",
                    String.class, id)).isEqualTo("UNKNOWN_ACTION");
        }
    }

    @Test
    void singleBacklogUserDoesNotStarveSmallUser(@TempDir Path directory) {
        try (ConfigurableApplicationContext context = start(directory)) {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            String backlogUser = UUID.randomUUID().toString();
            String smallUser = UUID.randomUUID().toString();
            List<String> backlog = new ArrayList<>();
            for (int index = 0; index < 100; index++) {
                backlog.add(command(jdbc, backlogUser, "PENDING", BASE.plusSeconds(index), null));
            }
            String small = command(jdbc, smallUser, "PENDING", BASE.plusSeconds(50), null);

            List<String> due = context.getBean(DeliveryMapper.class).findDueIds(NOW);

            assertThat(due).hasSize(20).contains(small);
            assertThat(due).containsAnyElementsOf(backlog);
        }
    }

    @Test
    void firstRoundsCoverEveryUser(@TempDir Path directory) {
        try (ConfigurableApplicationContext context = start(directory)) {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            String busy = UUID.randomUUID().toString();
            String medium = UUID.randomUUID().toString();
            String quiet = UUID.randomUUID().toString();
            List<String> commandIds = new ArrayList<>();
            for (int index = 0; index < 30; index++) {
                commandIds.add(command(jdbc, busy, "PENDING", BASE.plusSeconds(index), null));
            }
            for (int index = 0; index < 2; index++) {
                commandIds.add(command(jdbc, medium, "PENDING", BASE.plusSeconds(100 + index), null));
            }
            commandIds.add(command(jdbc, quiet, "PENDING", BASE.plusSeconds(200), null));

            List<String> due = context.getBean(DeliveryMapper.class).findDueIds(NOW);

            assertThat(due).hasSize(20)
                    .contains(commandIds.get(0), commandIds.get(30), commandIds.get(32));
        }
    }

    @Test
    void sameUserKeepsDueTimeThenIdOrder(@TempDir Path directory) {
        try (ConfigurableApplicationContext context = start(directory)) {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            String user = UUID.randomUUID().toString();
            String first = command(jdbc, user, "PENDING", BASE, null);
            String second = command(jdbc, user, "PENDING", BASE, null);
            String third = command(jdbc, user, "PENDING", BASE, null);

            List<String> due = context.getBean(DeliveryMapper.class).findDueIds(NOW);

            assertThat(due).containsExactly(first, second, third);
        }
    }

    @Test
    void expiredLeaseIsEligibleAndActiveLeaseIsNot(@TempDir Path directory) {
        try (ConfigurableApplicationContext context = start(directory)) {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            String user = UUID.randomUUID().toString();
            String expired = command(jdbc, user, "DELIVERING", BASE, BASE.plusMinutes(1));
            String active = command(jdbc, user, "DELIVERING", BASE, NOW.plusHours(1));
            String future = command(jdbc, user, "PENDING", NOW.plusHours(1), null);

            List<String> due = context.getBean(DeliveryMapper.class).findDueIds(NOW);

            assertThat(due).contains(expired).doesNotContain(active, future);
        }
    }

    @Test
    void disabledUserIsExcludedWhileUnregisteredLocalUserRemains(@TempDir Path directory) {
        try (ConfigurableApplicationContext context = start(directory)) {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            String disabled = UUID.randomUUID().toString();
            String localOnly = UUID.randomUUID().toString();
            jdbc.update("""
                    INSERT INTO sq_user (user_id, username, password_hash, role, status)
                    VALUES (?, ?, 'hash', 'USER', 'DISABLED')
                    """, disabled, "disabled-" + disabled);
            String disabledCommand = command(jdbc, disabled, "PENDING", BASE, null);
            String localCommand = command(jdbc, localOnly, "PENDING", BASE, null);

            List<String> due = context.getBean(DeliveryMapper.class).findDueIds(NOW);

            assertThat(due).contains(localCommand).doesNotContain(disabledCommand);
        }
    }

    private String command(JdbcTemplate jdbc, String userId, String status,
                           LocalDateTime dueAt, LocalDateTime leaseUntil) {
        String commandId = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO sq_command
                    (command_id, user_id, scope_key, idempotency_key, payload_hash,
                     action, payload_json, status, due_at, delivery_lease_until)
                VALUES (?, ?, ?, ?, ?, 'SUBMIT', '{}', ?, ?, ?)
                """,
                commandId,
                userId,
                "scope-" + commandId,
                "idem-" + commandId,
                "a".repeat(64),
                status,
                dueAt,
                leaseUntil);
        return commandId;
    }

    private ConfigurableApplicationContext start(Path directory) {
        return SpringApplication.run(AgentSquadApplication.class,
                "--spring.main.web-application-type=none",
                "--spring.datasource.url=" + com.agentsquad.squad.testsupport.MySqlTestDatabase.url(directory.resolve("squad")),
                "--spring.datasource.username=" + com.agentsquad.squad.testsupport.MySqlTestDatabase.username(),
                "--spring.datasource.password=" + com.agentsquad.squad.testsupport.MySqlTestDatabase.password(),
                "--spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
                "--agentsquad.delivery.enabled=false");
    }
}
