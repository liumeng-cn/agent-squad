package com.agentsquad.squad.testsupport;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 单文件完整建表验收。
 * <p>
 * 仅在本次分配的隔离 PostgreSQL 库执行基线，验证全部表、预置账号及重复执行不覆盖用户密码。
 */
class CompleteSchemaIntegrationTest {

    private static final int BUSINESS_TABLES = 53;

    @Test
    void createsCompleteSchemaAndKeepsChangedPasswordOnRepeat(@TempDir Path directory) throws Exception {
        String url = PostgresTestDatabase.url(directory);
        String script = new ClassPathResource("db/schema-postgresql.sql").getContentAsString(StandardCharsets.UTF_8);
        try (var connection = DriverManager.getConnection(url, PostgresTestDatabase.username(),
                PostgresTestDatabase.password());
             var statement = connection.createStatement()) {
            // 基线含 $$ 函数体，整段执行避免按分号切分破坏 PL/pgSQL。
            statement.execute(script);
            try (var rows = statement.executeQuery(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='public'")) {
                assertTrue(rows.next());
                assertEquals(BUSINESS_TABLES, rows.getInt(1));
            }
            try (var rows = statement.executeQuery(
                    "SELECT COUNT(*) FROM sq_user WHERE (username='admin' AND user_id=1) "
                            + "OR (username='demo' AND user_id=2)")) {
                assertTrue(rows.next());
                assertEquals(2, rows.getInt(1));
            }
            assertEquals(1, statement.executeUpdate(
                    "UPDATE sq_user SET password_hash='changed-test-password-hash' WHERE username='admin'"));
            statement.execute(script);
            try (var rows = statement.executeQuery("SELECT password_hash FROM sq_user WHERE username='admin'")) {
                assertTrue(rows.next());
                assertEquals("changed-test-password-hash", rows.getString(1));
            }
            try (var rows = statement.executeQuery("SELECT COUNT(*) FROM sq_user")) {
                assertTrue(rows.next());
                assertEquals(2, rows.getInt(1));
            }
        }
    }
}
