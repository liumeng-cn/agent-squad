package com.agentsquad.squad.runtime;

import com.agentsquad.squad.conversation.repository.ExecutionScopeMapper;
import db.migration.V8__BackfillConversationExecution;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 会话执行基础迁移验证。
 * <p>
 * 从真实 V6 结构升级，验证历史公开正文、预算守恒、身份不变、归属约束及幂等重放；不调用模型。
 */
class ConversationExecutionMigrationTest {

    private static final String USER = "00000000-0000-0000-0000-000000000001";

    private static final String OTHER_USER = "00000000-0000-0000-0000-000000000002";

    private static final String CONVERSATION = "10000000-0000-0000-0000-000000000001";

    private static final String TASK = "20000000-0000-0000-0000-000000000001";

    private static final String ACTIVE_TASK = "20000000-0000-0000-0000-000000000002";

    @Test
    void shouldPreserveLegacyFactsAndEnforceOwnership(@TempDir Path directory) throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource("jdbc:h2:file:"
                + directory.resolve("migration") + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE", "sa", "");
        verifyUpgrade(dataSource);
    }

    @Test
    void malformedHistoricalInputRollsBackAllBackfill(@TempDir Path directory) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource("jdbc:h2:file:"
                + directory.resolve("rollback") + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE", "sa", "");
        Flyway.configure().dataSource(dataSource).target(MigrationVersion.fromVersion("6")).load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        seedLegacyFacts(jdbc);
        Flyway.configure().dataSource(dataSource).target(MigrationVersion.fromVersion("7")).load().migrate();
        jdbc.update("UPDATE sq_goal_task SET input_json = '[]' WHERE task_id = ?", ACTIVE_TASK);

        assertThatThrownBy(() -> Flyway.configure().dataSource(dataSource).load().migrate())
                .hasStackTraceContaining("历史公开记录不是受支持的 JSON 对象");
        for (String table : List.of("sq_execution_scope", "sq_budget_account", "sq_message", "sq_conversation_event")) {
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class)).as(table).isZero();
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_goal_task WHERE scope_id IS NOT NULL", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_budget_reservation WHERE status = 'UNKNOWN'",
                Integer.class)).isEqualTo(1);
    }

    /**
     * 可由专用 MySQL 测试复用同一验收；调用方必须提供全新隔离库，不得指向实际开发数据。
     */
    static void verifyUpgrade(DriverManagerDataSource dataSource) throws Exception {
        Flyway.configure().dataSource(dataSource).target(MigrationVersion.fromVersion("6")).load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        seedLegacyFacts(jdbc);
        List<Map<String, Object>> bindings = jdbc.queryForList(
                "SELECT user_id, binding_id, framework_session_id, native_event_cursor FROM sq_runtime_binding ORDER BY id");
        List<Map<String, Object>> commands = jdbc.queryForList("""
                SELECT command_id, idempotency_key, task_id, binding_id, status, native_turn_id, native_run_id
                FROM sq_command ORDER BY id
                """);
        List<Map<String, Object>> reservations = jdbc.queryForList("""
                SELECT reservation_id, task_id, status, reserved_units, settled_units, pricing_version,
                       request_hash FROM sq_budget_reservation ORDER BY id
                """);
        long total = jdbc.queryForObject("SELECT SUM(CASE WHEN status = 'SETTLED' THEN settled_units "
                + "ELSE reserved_units END) FROM sq_budget_reservation", Long.class);

        Flyway flyway = Flyway.configure().dataSource(dataSource).load();
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT user_id, binding_id, framework_session_id, native_event_cursor "
                + "FROM sq_runtime_binding ORDER BY id")).isEqualTo(bindings);
        assertThat(jdbc.queryForList("SELECT command_id, idempotency_key, task_id, binding_id, status, "
                + "native_turn_id, native_run_id FROM sq_command ORDER BY id")).isEqualTo(commands);
        assertThat(jdbc.queryForList("SELECT reservation_id, task_id, status, reserved_units, settled_units, "
                + "pricing_version, request_hash FROM sq_budget_reservation ORDER BY id")).isEqualTo(reservations);
        assertThat(jdbc.queryForObject("SELECT SUM(CASE WHEN r.status = 'SETTLED' THEN r.settled_units "
                + "ELSE r.reserved_units END) FROM sq_budget_reservation r JOIN sq_budget_account a "
                + "ON a.user_id = r.user_id AND a.account_id = r.account_id", Long.class)).isEqualTo(total);
        assertThat(total).isEqualTo(80);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_execution_scope", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_budget_account", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_response_execution", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_goal_task", Integer.class)).isEqualTo(3);

        assertThat(jdbc.queryForList("SELECT content FROM sq_message WHERE user_id = ? ORDER BY message_seq",
                String.class, USER)).containsExactly("第一条真实问题", "最终公开答案", "在途问题");
        Map<String, Object> answer = jdbc.queryForMap("SELECT revision, completed FROM sq_message "
                + "WHERE user_id = ? AND role = 'ASSISTANT'", USER);
        assertThat(((Number) answer.get("revision")).longValue()).isEqualTo(2);
        assertThat(answer.get("completed")).isEqualTo(true);
        assertThat(jdbc.queryForObject("SELECT message_seq FROM sq_conversation WHERE user_id = ?",
                Long.class, USER)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT conversation_event_seq FROM sq_conversation WHERE user_id = ?",
                Long.class, USER)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_message_artifact", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT del_flag FROM sq_message WHERE user_id = ?",
                String.class, OTHER_USER)).isEqualTo("1");
        assertThat(jdbc.queryForObject("SELECT active_task_id FROM sq_conversation WHERE user_id = ?",
                String.class, USER)).isEqualTo(ACTIVE_TASK);
        for (String table : List.of("sq_runtime_binding", "sq_command", "sq_operation", "sq_interaction",
                "sq_context_snapshot", "sq_budget_reservation")) {
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE task_id IS NOT NULL "
                    + "AND scope_id IS NULL", Integer.class)).as(table).isZero();
        }
        verifyOwnership(dataSource, jdbc);

        // 重启的 Flyway 不再执行；额外直接重放 DML 也不能重复消息、事件、账户或命令。
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        List<Map<String, Object>> messages = jdbc.queryForList("SELECT * FROM sq_message ORDER BY id");
        try (Connection connection = dataSource.getConnection()) {
            V8__BackfillConversationExecution migration = new V8__BackfillConversationExecution();
            migration.migrate(new ConversationMigrationContext(connection));
        }
        assertThat(jdbc.queryForList("SELECT * FROM sq_message ORDER BY id")).isEqualTo(messages);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_budget_account", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_conversation_event", Integer.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_command", Integer.class)).isEqualTo(1);
    }

    private static void verifyOwnership(DriverManagerDataSource dataSource, JdbcTemplate jdbc) {
        String scopeId = jdbc.queryForObject("SELECT scope_id FROM sq_goal_task WHERE user_id = ? AND task_id = ?",
                String.class, USER, TASK);
        Configuration configuration = new Configuration(new Environment("migration-test",
                new JdbcTransactionFactory(), dataSource));
        configuration.addMapper(ExecutionScopeMapper.class);
        SqlSessionFactory factory = new SqlSessionFactoryBuilder().build(configuration);
        try (SqlSession session = factory.openSession()) {
            ExecutionScopeMapper mapper = session.getMapper(ExecutionScopeMapper.class);
            assertThat(mapper.findActiveOwned(USER, scopeId).ownerId()).isEqualTo(TASK);
            assertThat(mapper.findActiveOwned(OTHER_USER, scopeId)).isNull();
            String deletedScope = jdbc.queryForObject("SELECT scope_id FROM sq_goal_task WHERE user_id = ?",
                    String.class, OTHER_USER);
            assertThat(mapper.findActiveOwned(OTHER_USER, deletedScope)).isNull();
        }
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO sq_execution_scope (scope_id, user_id, conversation_id, conversation_generation,
                                                kind, owner_id, account_id)
                SELECT ?, ?, conversation_id, 0, 'RESPONSE', ?, account_id FROM sq_execution_scope
                WHERE user_id = ? AND scope_id = ?
                """, UUID.randomUUID().toString(), OTHER_USER, UUID.randomUUID().toString(), USER, scopeId))
                .isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update("UPDATE sq_goal_task SET del_flag = '1' WHERE user_id = ? AND task_id = ?", USER, TASK);
        try (SqlSession session = factory.openSession()) {
            assertThat(session.getMapper(ExecutionScopeMapper.class).findActiveOwned(USER, scopeId)).isNull();
        }
        jdbc.update("UPDATE sq_goal_task SET del_flag = '0' WHERE user_id = ? AND task_id = ?", USER, TASK);
        jdbc.update("UPDATE sq_conversation SET generation = generation + 1 WHERE user_id = ?", USER);
        try (SqlSession session = factory.openSession()) {
            assertThat(session.getMapper(ExecutionScopeMapper.class).findActiveOwned(USER, scopeId)).isNull();
        }
        jdbc.update("UPDATE sq_conversation SET generation = generation - 1 WHERE user_id = ?", USER);
    }

    private static void seedLegacyFacts(JdbcTemplate jdbc) {
        for (String userId : List.of(USER, OTHER_USER)) {
            jdbc.update("INSERT INTO sq_conversation (conversation_id, user_id, title, status, generation) "
                            + "VALUES (?, ?, '旧对话', ?, 3)", CONVERSATION, userId,
                    USER.equals(userId) ? "ACTIVE" : "DELETED");
            jdbc.update("""
                            INSERT INTO sq_goal_task (task_id, user_id, conversation_id, status, input_json,
                                                       completion_proposal_json, budget_limit_microunits, create_time)
                            VALUES (?, ?, ?, 'SUCCEEDED', ?, ?, 100, '2026-10-27 00:00:00')
                            """, TASK, userId, CONVERSATION, "{\"message\":\"第一条真实问题\"}",
                    "{\"text\":\"不能公开的候选\"}");
        }
        jdbc.update("""
                INSERT INTO sq_goal_task (task_id, user_id, conversation_id, status, input_json, create_time)
                VALUES (?, ?, ?, 'RUNNING', ?, '2026-10-27 00:03:00')
                """, ACTIVE_TASK, USER, CONVERSATION, "{\"message\":\"在途问题\"}");
        jdbc.update("UPDATE sq_conversation SET active_task_id = ? WHERE user_id = ?", ACTIVE_TASK, USER);
        String bindingId = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO sq_runtime_binding (binding_id, user_id, task_id, role, agent_id,
                                                framework_session_id, status, native_event_cursor)
                VALUES (?, ?, ?, 'MASTER', ?, ?, 'ACTIVE', 17)
                """, bindingId, USER, TASK, "a".repeat(32), UUID.randomUUID().toString());
        jdbc.update("""
                INSERT INTO sq_command (command_id, user_id, scope_key, idempotency_key, payload_hash,
                                        task_id, binding_id, action, payload_json, status, due_at,
                                        native_turn_id, native_run_id)
                VALUES (?, ?, 'task.create:legacy', 'original-submit-key', ?, ?, ?, 'SUBMIT', '{}',
                        'DELIVERED', CURRENT_TIMESTAMP, 'original-turn', 'original-run')
                """, UUID.randomUUID().toString(), USER, "b".repeat(64), TASK, bindingId);
        insertAnswer(jdbc, 1, "answer.replaced", "半答案", 1, "2026-10-27 00:01:00");
        insertAnswer(jdbc, 2, "answer.completed", "最终公开答案", 2, "2026-10-27 00:02:00");
        insertAnswer(jdbc, 3, "internal.tool", "不能公开的工具输出", 3, "2026-10-27 00:02:01");
        for (String status : List.of("SETTLED", "UNKNOWN", "RESERVED")) {
            jdbc.update("""
                            INSERT INTO sq_budget_reservation (reservation_id, user_id, task_id, usage_key, kind,
                                                                reserved_units, settled_units, status, pricing_version)
                            VALUES (?, ?, ?, ?, 'MODEL', ?, ?, ?, 'legacy-price-v1')
                            """, UUID.randomUUID().toString(), USER, TASK, "model:" + status,
                    "SETTLED".equals(status) ? 40 : "UNKNOWN".equals(status) ? 50 : 20,
                    "SETTLED".equals(status) ? 10 : 0, status);
        }
        String artifact = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO sq_artifact (artifact_id, user_id, file_name, media_type, size_bytes,
                                         sha256, upload_key, content_bytes, expires_at)
                VALUES (?, ?, 'legacy.txt', 'text/plain', 1, ?, 'old-upload', ?, '2026-12-01')
                """, artifact, USER, "c".repeat(64), new byte[]{65});
        jdbc.update("INSERT INTO sq_task_artifact (user_id, task_id, artifact_id) VALUES (?, ?, ?)", USER, TASK, artifact);
        jdbc.update("""
                INSERT INTO sq_operation (operation_id, user_id, task_id, binding_id, framework_turn_id,
                                          tool_call_id, tool_key, tool_version, effect, request_hash,
                                          arguments_json, status)
                VALUES (?, ?, ?, ?, 'original-turn', 'call-1', 'read', '1', 'READ', ?, '{}', 'SUCCEEDED')
                """, UUID.randomUUID().toString(), USER, TASK, bindingId, "d".repeat(64));
        jdbc.update("""
                INSERT INTO sq_interaction (interaction_id, user_id, task_id, binding_id, origin, kind,
                                            question, answer_schema_json, request_hash, expires_at, status)
                VALUES (?, ?, ?, ?, 'BUSINESS', 'INPUT', '材料', '{}', ?, '2026-12-01', 'RESPONDED')
                """, UUID.randomUUID().toString(), USER, TASK, bindingId, "e".repeat(64));
        jdbc.update("""
                INSERT INTO sq_context_snapshot (snapshot_id, user_id, task_id, target_node_key, logical_key,
                                                 conversation_generation, items_json, token_estimate, content_hash)
                VALUES (?, ?, ?, 'node-1', 'context-1', 3, '[]', 1, ?)
                """, UUID.randomUUID().toString(), USER, TASK, "f".repeat(64));
    }

    private static void insertAnswer(JdbcTemplate jdbc, long sequence, String type, String text,
                                     long revision, String time) {
        jdbc.update("""
                        INSERT INTO sq_task_event (user_id, task_id, event_seq, producer_key, event_type,
                                                   payload_json, occurred_at) VALUES (?, ?, ?, ?, ?, ?, ?)
                        """, USER, TASK, sequence, "legacy:" + sequence, type,
                "{\"text\":\"" + text + "\",\"revision\":" + revision + "}", time);
    }
}
