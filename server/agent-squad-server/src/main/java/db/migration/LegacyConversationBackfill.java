package db.migration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 历史会话事实回填器。
 * <p>
 * 使用稳定业务ID及来源键幂等回填；只操作迁移连接，不依赖可演进的应用服务，不触发模型或外部操作。
 */
final class LegacyConversationBackfill {

    private static final List<String> SCOPE_TABLES = List.of("sq_runtime_binding", "sq_command",
            "sq_operation", "sq_interaction", "sq_context_snapshot");

    private final Connection connection;

    private final ObjectMapper objectMapper = new ObjectMapper();

    LegacyConversationBackfill(Connection connection) {
        this.connection = connection;
    }

    /**
     * 按会话整理历史前缀，先确保预算与作用域再写消息及水位；失败交由 Flyway 回滚。
     */
    void migrate() throws Exception {
        try (PreparedStatement query = connection.prepareStatement("""
                SELECT user_id, conversation_id, generation, status, del_flag
                FROM sq_conversation ORDER BY id
                """); ResultSet conversations = query.executeQuery()) {
            while (conversations.next()) {
                String userId = conversations.getString("user_id");
                String conversationId = conversations.getString("conversation_id");
                String deleted = !"ACTIVE".equals(conversations.getString("status"))
                        || "1".equals(conversations.getString("del_flag")) ? "1" : "0";
                List<LegacyMessageBackfillRow> messages = collectMessages(userId, conversationId,
                        conversations.getLong("generation"), deleted);
                messages.sort(Comparator.comparing(LegacyMessageBackfillRow::occurredAt)
                        .thenComparingLong(LegacyMessageBackfillRow::taskStorageId)
                        .thenComparing(message -> "USER".equals(message.role()) ? 0 : 1));
                for (LegacyMessageBackfillRow message : messages) {
                    insertMessage(message);
                }
                copyAttachments(userId, conversationId);
                updateConversationWatermarks(userId, conversationId);
            }
        }
    }

    private List<LegacyMessageBackfillRow> collectMessages(String userId, String conversationId,
                                                           long generation, String conversationDeleted)
            throws Exception {
        List<LegacyMessageBackfillRow> messages = new ArrayList<>();
        try (PreparedStatement query = connection.prepareStatement("""
                SELECT id, task_id, input_json, budget_profile_key, budget_limit_microunits,
                       create_time, del_flag FROM sq_goal_task
                WHERE user_id = ? AND conversation_id = ? ORDER BY id
                """)) {
            query.setString(1, userId);
            query.setString(2, conversationId);
            try (ResultSet tasks = query.executeQuery()) {
                while (tasks.next()) {
                    String taskId = tasks.getString("task_id");
                    String deleted = "1".equals(conversationDeleted) ? "1" : tasks.getString("del_flag");
                    String messageId = stableId("message:user", userId, taskId);
                    ensureScopeAndAccount(userId, conversationId, generation, taskId,
                            tasks.getString("budget_profile_key"), tasks.getLong("budget_limit_microunits"));
                    JsonNode input = readJson(tasks.getString("input_json"));
                    messages.add(new LegacyMessageBackfillRow(userId, conversationId, taskId, tasks.getLong("id"),
                            messageId, "USER", null, input.path("message").asText(""), 0, true,
                            tasks.getTimestamp("create_time").toLocalDateTime(), "task:user:" + taskId, deleted));
                    addLatestAnswer(messages, userId, conversationId, taskId, tasks.getLong("id"), messageId, deleted);
                }
            }
        }
        return messages;
    }

    /**
     * 为旧目标共享同一账户，迁移只补引用，预留与结算金额继续以原 Reservation 为事实。
     */
    private void ensureScopeAndAccount(String userId, String conversationId, long generation, String taskId,
                                       String profileKey, long limit) throws SQLException {
        String accountId = stableId("account", userId, taskId);
        String scopeId = stableId("scope:task", userId, taskId);
        execute("""
                INSERT INTO sq_budget_account (account_id, user_id, limit_microunits, profile_key, status,
                                               creator_id, last_updater_id)
                SELECT ?, ?, ?, ?, 'OPEN', ?, ?
                WHERE NOT EXISTS (SELECT 1 FROM sq_budget_account WHERE user_id = ? AND account_id = ?)
                """, accountId, userId, limit, profileKey, userId, userId, userId, accountId);
        execute("""
                        INSERT INTO sq_execution_scope (scope_id, user_id, conversation_id, conversation_generation,
                                                        kind, owner_id, account_id, creator_id, last_updater_id)
                        SELECT ?, ?, ?, ?, 'TASK', ?, ?, ?, ?
                        WHERE NOT EXISTS (SELECT 1 FROM sq_execution_scope WHERE user_id = ? AND scope_id = ?)
                        """, scopeId, userId, conversationId, generation, taskId, accountId,
                userId, userId, userId, scopeId);
        execute("UPDATE sq_goal_task SET scope_id = ? WHERE user_id = ? AND task_id = ? AND scope_id IS NULL",
                scopeId, userId, taskId);
        // 来源消息外键在插入消息后回填，避免制造循环依赖或伪造旧回复执行。
        for (String table : SCOPE_TABLES) {
            execute("UPDATE " + table + " SET scope_id = ? WHERE user_id = ? AND task_id = ? AND scope_id IS NULL",
                    scopeId, userId, taskId);
        }
        execute("""
                UPDATE sq_budget_reservation SET scope_id = ?, account_id = ?
                WHERE user_id = ? AND task_id = ? AND scope_id IS NULL AND account_id IS NULL
                """, scopeId, accountId, userId, taskId);
    }

    private void addLatestAnswer(List<LegacyMessageBackfillRow> messages, String userId, String conversationId,
                                 String taskId, long storageId, String sourceMessageId, String deleted)
            throws Exception {
        try (PreparedStatement query = connection.prepareStatement("""
                SELECT event_type, payload_json, occurred_at FROM sq_task_event
                WHERE user_id = ? AND task_id = ? AND event_type IN ('answer.completed', 'answer.replaced')
                ORDER BY event_seq DESC LIMIT 1
                """)) {
            query.setString(1, userId);
            query.setString(2, taskId);
            try (ResultSet answers = query.executeQuery()) {
                if (!answers.next()) {
                    return;
                }
                JsonNode payload = readJson(answers.getString("payload_json"));
                messages.add(new LegacyMessageBackfillRow(userId, conversationId, taskId, storageId,
                        stableId("message:assistant", userId, taskId), "ASSISTANT", sourceMessageId,
                        payload.path("text").asText(""), payload.path("revision").asLong(0),
                        "answer.completed".equals(answers.getString("event_type")),
                        answers.getTimestamp("occurred_at").toLocalDateTime(), "task:assistant:" + taskId, deleted));
            }
        }
    }

    private void insertMessage(LegacyMessageBackfillRow message) throws Exception {
        if (exists("SELECT 1 FROM sq_message WHERE user_id = ? AND legacy_key = ?",
                message.userId(), message.legacyKey())) {
            return;
        }
        long sequence = nextSequence("sq_message", "message_seq", message.userId(), message.conversationId());
        execute("""
                        INSERT INTO sq_message (message_id, user_id, conversation_id, message_seq, role, content,
                                                revision, completed, source_message_id, task_id, occurred_at,
                                                legacy_key, del_flag, creator_id, last_updater_id)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """, message.messageId(), message.userId(), message.conversationId(), sequence, message.role(),
                message.content(), message.revision(), message.completed(), message.sourceMessageId(),
                message.taskId(), message.occurredAt(), message.legacyKey(), message.deleted(),
                message.userId(), message.userId());
        if ("USER".equals(message.role())) {
            execute("""
                    UPDATE sq_goal_task SET origin_message_id = ?
                    WHERE user_id = ? AND task_id = ? AND origin_message_id IS NULL
                    """, message.messageId(), message.userId(), message.taskId());
        }
        String payload = objectMapper.writeValueAsString(Map.of("messageId", message.messageId(),
                "revision", message.revision()));
        execute("""
                        INSERT INTO sq_conversation_event (user_id, conversation_id, generation, event_seq,
                                                           producer_key, event_type, payload_json, occurred_at,
                                                           del_flag, creator_id, last_updater_id)
                        SELECT ?, ?, generation, ?, ?, ?, ?, ?, ?, ?, ? FROM sq_conversation
                        WHERE user_id = ? AND conversation_id = ?
                        """, message.userId(), message.conversationId(),
                nextSequence("sq_conversation_event", "event_seq", message.userId(), message.conversationId()),
                "migration:" + message.legacyKey(), "USER".equals(message.role())
                        ? "message.accepted" : message.completed() ? "message.completed" : "message.updated",
                payload, message.occurredAt(), message.deleted(), message.userId(), message.userId(),
                message.userId(), message.conversationId());
    }

    private void copyAttachments(String userId, String conversationId) throws SQLException {
        execute("""
                INSERT INTO sq_message_artifact (user_id, message_id, artifact_id, del_flag,
                                                 creator_id, last_updater_id)
                SELECT m.user_id, m.message_id, a.artifact_id, m.del_flag, m.user_id, m.user_id
                FROM sq_message m JOIN sq_task_artifact a ON m.user_id = a.user_id AND m.task_id = a.task_id
                WHERE m.user_id = ? AND m.conversation_id = ? AND m.role = 'USER'
                  AND NOT EXISTS (SELECT 1 FROM sq_message_artifact ma WHERE ma.user_id = m.user_id
                                  AND ma.message_id = m.message_id AND ma.artifact_id = a.artifact_id)
                """, userId, conversationId);
    }

    private void updateConversationWatermarks(String userId, String conversationId) throws SQLException {
        execute("""
                UPDATE sq_conversation SET message_seq = (SELECT COALESCE(MAX(m.message_seq), 0)
                    FROM sq_message m WHERE m.user_id = ? AND m.conversation_id = ?),
                    conversation_event_seq = (SELECT COALESCE(MAX(e.event_seq), 0)
                    FROM sq_conversation_event e WHERE e.user_id = ? AND e.conversation_id = ?),
                    last_activity_at = COALESCE((SELECT MAX(m.occurred_at) FROM sq_message m
                    WHERE m.user_id = ? AND m.conversation_id = ?), create_time)
                WHERE user_id = ? AND conversation_id = ?
                """, userId, conversationId, userId, conversationId, userId, conversationId, userId, conversationId);
    }

    private JsonNode readJson(String value) throws SQLException {
        try {
            JsonNode node = objectMapper.readTree(value);
            // H2 的 JSON 经 JDBC 返回字符串包装；MySQL 返回原 JSON 文本。
            if (node != null && node.isTextual()) {
                node = objectMapper.readTree(node.textValue());
            }
            if (node == null || !node.isObject()) {
                throw new SQLException("历史公开记录不是受支持的 JSON 对象，回填已终止");
            }
            return node;
        } catch (JsonProcessingException exception) {
            // Jackson 的原异常可能包含用户正文；迁移错误只保留可定位的分类。
            throw new SQLException("历史公开记录 JSON 无法解析，回填已终止", "22032");
        }
    }

    private boolean exists(String sql, Object... arguments) throws SQLException {
        try (PreparedStatement statement = prepare(sql, arguments); ResultSet rows = statement.executeQuery()) {
            return rows.next();
        }
    }

    private long nextSequence(String table, String column, String userId, String conversationId) throws SQLException {
        try (PreparedStatement statement = prepare("SELECT COALESCE(MAX(" + column + "), 0) + 1 FROM " + table
                + " WHERE user_id = ? AND conversation_id = ?", userId, conversationId);
             ResultSet rows = statement.executeQuery()) {
            rows.next();
            return rows.getLong(1);
        }
    }

    private void execute(String sql, Object... arguments) throws SQLException {
        try (PreparedStatement statement = prepare(sql, arguments)) {
            statement.executeUpdate();
        }
    }

    private PreparedStatement prepare(String sql, Object... arguments) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(sql);
        try {
            for (int index = 0; index < arguments.length; index++) {
                statement.setObject(index + 1, arguments[index]);
            }
            return statement;
        } catch (SQLException exception) {
            statement.close();
            throw exception;
        }
    }

    private String stableId(String kind, String userId, String taskId) {
        return UUID.nameUUIDFromBytes(("agent-squad:legacy:" + kind + ":" + userId + ":" + taskId)
                .getBytes(StandardCharsets.UTF_8)).toString();
    }
}
