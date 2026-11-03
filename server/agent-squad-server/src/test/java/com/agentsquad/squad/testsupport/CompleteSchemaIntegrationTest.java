package com.agentsquad.squad.testsupport;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.nio.file.Path;
import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 单文件完整建表验收。
 * <p>
 * 仅在本次分配的隔离MySQL库执行schema，验证全部表、预置账号及重复执行不覆盖用户密码。
 */
class CompleteSchemaIntegrationTest {
    @Test
    void createsCompleteSchemaAndKeepsChangedPasswordOnRepeat(@TempDir Path directory) throws Exception {
        String url = MySqlTestDatabase.url(directory);
        try (var connection = DriverManager.getConnection(url, MySqlTestDatabase.username(), MySqlTestDatabase.password())) {
            var schema = new ClassPathResource("db/schema.sql");
            ScriptUtils.executeSqlScript(connection, schema);
            try (var statement = connection.createStatement();
                 var rows = statement.executeQuery("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()")) {
                assertTrue(rows.next());
                assertEquals(51, rows.getInt(1));
            }
            try (var statement = connection.createStatement();
                 var rows = statement.executeQuery("SELECT COUNT(*) FROM sq_user WHERE (username='admin' AND user_id='10000000-0000-0000-0000-000000000001') OR (username='demo' AND user_id='10000000-0000-0000-0000-000000000002')")) {
                assertTrue(rows.next());
                assertEquals(2, rows.getInt(1));
            }
            try (var statement = connection.prepareStatement("UPDATE sq_user SET password_hash=? WHERE username='admin'")) {
                statement.setString(1, "changed-test-password-hash");
                assertEquals(1, statement.executeUpdate());
            }
            ScriptUtils.executeSqlScript(connection, schema);
            try (var statement = connection.createStatement();
                 var rows = statement.executeQuery("SELECT password_hash FROM sq_user WHERE username='admin'")) {
                assertTrue(rows.next());
                assertEquals("changed-test-password-hash", rows.getString(1));
            }
            try (var statement = connection.createStatement();
                 var rows = statement.executeQuery("SELECT COUNT(*) FROM sq_user")) {
                assertTrue(rows.next());
                assertEquals(2, rows.getInt(1));
            }
        }
    }
}
