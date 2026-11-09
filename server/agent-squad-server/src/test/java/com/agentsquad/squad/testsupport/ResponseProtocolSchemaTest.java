package com.agentsquad.squad.testsupport;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 回复完成协议列的默认值验证。
 * <p>
 * PostgreSQL 采用单一完整基线，不再回放旧版本迁移；此处确认旧写入路径省略协议列时
 * 仍按 {@code PROPOSAL} 落库，避免历史执行记录被解释成自然输出。
 */
class ResponseProtocolSchemaTest {

    @Test
    void legacyInsertDefaultsToProposalProtocol(@TempDir Path directory) throws Exception {
        String url = PostgresTestDatabase.url(directory);
        try (var connection = DriverManager.getConnection(url, PostgresTestDatabase.username(),
                PostgresTestDatabase.password());
             var statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO sq_response_execution "
                    + "(execution_id,user_id,conversation_id,source_message_id,scope_id,binding_id,status) "
                    + "VALUES (101,201,301,401,501,601,'RUNNING')");
            try (var rows = statement.executeQuery(
                    "SELECT completion_protocol,status FROM sq_response_execution WHERE execution_id=101")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("completion_protocol")).isEqualTo("PROPOSAL");
                assertThat(rows.getString("status")).isEqualTo("RUNNING");
            }
            try (var rows = statement.executeQuery("SELECT data_type,character_maximum_length,is_nullable,"
                    + "column_default FROM information_schema.columns "
                    + "WHERE table_schema='public' AND table_name='sq_response_execution' "
                    + "AND column_name='completion_protocol'")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("data_type")).isEqualTo("character varying");
                assertThat(rows.getInt("character_maximum_length")).isEqualTo(16);
                assertThat(rows.getString("is_nullable")).isEqualTo("NO");
                assertThat(rows.getString("column_default")).contains("PROPOSAL");
            }
        }
    }
}
