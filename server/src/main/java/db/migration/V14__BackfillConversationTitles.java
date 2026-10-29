package db.migration;

import com.agentsquad.squad.conversation.domain.ConversationTitles;
import com.agentsquad.squad.common.util.RuntimePayloads;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Set;

/**
 * 历史空白主题与通用标题的安全补全。
 * <p>
 * 仅从已存在的首条用户消息或原目标原文取短主题，不调用模型，不覆盖用户已有的有效自定义标题。
 */
public class V14__BackfillConversationTitles extends BaseJavaMigration {
    @Override
    public Integer getChecksum() {
        return 2026102901;
    }

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        try (PreparedStatement query = connection.prepareStatement("""
                SELECT user_id,
                       conversation_id,
                       title,
                       title_source
                FROM sq_conversation
                """); ResultSet rows = query.executeQuery()) {
            while (rows.next()) {
                String source = rows.getString("title_source");
                String title = ConversationTitles.safe(rows.getString("title"), 200);
                if ("MANUAL".equals(source) || !title.isBlank() && !Set.of("新对话", "新的对话", "未命名对话").contains(title)) {
                    continue;
                }
                String user = rows.getString("user_id");
                String conversation = rows.getString("conversation_id");
                String content = firstInput(connection, user, conversation);
                String fallback = ConversationTitles.safe(content, 32);
                if (fallback.isBlank()) {
                    fallback = "新对话";
                }
                try (PreparedStatement update = connection.prepareStatement("""
                        UPDATE sq_conversation
                        SET title         = ?,
                            title_source  = 'FALLBACK',
                            title_version = title_version + 1
                        WHERE user_id = ?
                          AND conversation_id = ?
                          AND title_source <> 'MANUAL'
                        """)) {
                    update.setString(1, fallback);
                    update.setString(2, user);
                    update.setString(3, conversation);
                    update.executeUpdate();
                }
            }
        }
        try (PreparedStatement dates = connection.prepareStatement("""
                UPDATE sq_conversation
                SET last_activity_at = COALESCE((SELECT MAX(m.occurred_at) FROM sq_message m
                    WHERE m.user_id = sq_conversation.user_id AND m.conversation_id = sq_conversation.conversation_id),create_time)
                WHERE last_activity_at IS NULL
                """)) {
            dates.executeUpdate();
        }
    }

    private String firstInput(Connection connection, String userId, String conversationId) throws Exception {
        try (PreparedStatement query = connection.prepareStatement("""
                SELECT content
                FROM sq_message
                WHERE user_id = ?
                  AND conversation_id = ?
                  AND role = 'USER'
                ORDER BY message_seq
                LIMIT 1
                """)) {
            query.setString(1, userId);
            query.setString(2, conversationId);
            try (ResultSet row = query.executeQuery()) {
                if (row.next()) {
                    return row.getString(1);
                }
            }
        }
        try (PreparedStatement query = connection.prepareStatement("""
                SELECT input_json
                FROM sq_goal_task
                WHERE user_id = ?
                  AND conversation_id = ?
                ORDER BY id
                LIMIT 1
                """)) {
            query.setString(1, userId);
            query.setString(2, conversationId);
            try (ResultSet row = query.executeQuery()) {
                return row.next() ? RuntimePayloads.readJson(new ObjectMapper(), row.getString(1)).path("message").asText() : "";
            }
        }
    }
}
