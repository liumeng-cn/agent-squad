package com.agentsquad.squad.testsupport;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.nio.file.Path;
import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 自然回复协议的隔离迁移验证
 * <p>
 * 从最终结构构造缺少协议列的旧表，验证V7不改写旧回复语义；不将此实验冒充缺失的V1至V4完整迁移链验证。
 */
class NaturalReplySchemaTest {
    @Test
    void migrationAddsLegacyDefaultAndMatchesFinalColumn(@TempDir Path directory) throws Exception {
        try (var connection = DriverManager.getConnection(MySqlTestDatabase.url(directory), MySqlTestDatabase.username(), MySqlTestDatabase.password())) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V1__baseline.sql"));
            String expected;
            try (var statement = connection.createStatement(); var result = statement.executeQuery("SHOW FULL COLUMNS FROM sq_response_execution LIKE 'completion_protocol'")) {
                assertThat(result.next()).isTrue();
                expected = result.getString("Type") + ":" + result.getString("Null") + ":" + result.getString("Default") + ":" + result.getString("Comment");
            }
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("ALTER TABLE sq_response_execution DROP COLUMN completion_protocol");
                statement.executeUpdate("INSERT INTO sq_response_execution (execution_id,user_id,conversation_id,source_message_id,scope_id,binding_id,status) VALUES ('old-execution','test-user','test-conversation','test-message','test-scope','test-binding','RUNNING')");
            }
            Path root = Path.of("").toAbsolutePath();
            while (!java.nio.file.Files.exists(root.resolve("deploy/db/migrations/V7__natural_reply_protocol.sql"))) {
                root = root.getParent();
                if (root == null) throw new IllegalStateException("缺少V7脚本");
            }
            ScriptUtils.executeSqlScript(connection, new FileSystemResource(root.resolve("deploy/db/migrations/V7__natural_reply_protocol.sql")));
            try (var statement = connection.createStatement(); var result = statement.executeQuery("SELECT completion_protocol,status FROM sq_response_execution WHERE execution_id='old-execution'")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString("completion_protocol")).isEqualTo("PROPOSAL");
                assertThat(result.getString("status")).isEqualTo("RUNNING");
            }
            try (var statement = connection.createStatement(); var result = statement.executeQuery("SHOW FULL COLUMNS FROM sq_response_execution LIKE 'completion_protocol'")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString("Type") + ":" + result.getString("Null") + ":" + result.getString("Default") + ":" + result.getString("Comment")).isEqualTo(expected);
            }
        }
    }
}
