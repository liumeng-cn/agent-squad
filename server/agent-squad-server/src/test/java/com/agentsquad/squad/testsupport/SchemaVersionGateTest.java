package com.agentsquad.squad.testsupport;

import com.agentsquad.squad.AgentSquadApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 生产 profile 的启动结构门禁。
 * <p>验证生产不会在启动时执行 DDL：缺少迁移版本时拒绝启动且不建表；
 * 版本已应用时正常启动并保持表数量不变。</p>
 */
class SchemaVersionGateTest {

    @Test
    void rejectsStartupWhenMigrationVersionMissing() throws Exception {
        String url = MySqlTestDatabase.url(Path.of("target", "schema-gate", "empty"));

        Exception failure = assertThrows(Exception.class, () -> start(url));

        assertTrue(messageChain(failure).contains("生产启动要求已执行版本化迁移"), messageChain(failure));
        assertEquals(0, countTables(url), "生产启动失败时不得写入任何 DDL");
    }

    @Test
    void startsWithoutDdlWhenMigrationVersionApplied() throws Exception {
        String url = MySqlTestDatabase.url(Path.of("target", "schema-gate", "applied"));
        try (Connection connection = DriverManager.getConnection(url, MySqlTestDatabase.username(),
                MySqlTestDatabase.password())) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V1__baseline.sql"));
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("CREATE TABLE flyway_schema_history ("
                        + "installed_rank int NOT NULL, version varchar(50), description varchar(200) NOT NULL, "
                        + "type varchar(20) NOT NULL, script varchar(1000) NOT NULL, checksum int, "
                        + "installed_by varchar(100) NOT NULL, installed_on timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                        + "execution_time int NOT NULL, success tinyint(1) NOT NULL, PRIMARY KEY (installed_rank))");
                statement.executeUpdate("INSERT INTO flyway_schema_history (installed_rank, version, description, "
                        + "type, script, installed_by, execution_time, success) VALUES "
                        + "(1, '1', 'initial', 'SQL', 'V1__initial_44_tables.sql', 'test', 0, 1), "
                        + "(2, '2', 'multiuser identity', 'SQL', 'V2__multiuser_identity.sql', 'test', 0, 1)");
            }
        }

        try (ConfigurableApplicationContext context = start(url)) {
            assertTrue(context.isActive(), "版本已应用时生产上下文应正常启动");
        }
        assertEquals(49, countTables(url), "生产启动只校验版本，不得创建额外业务表");
    }

    private ConfigurableApplicationContext start(String url) {
        return SpringApplication.run(AgentSquadApplication.class,
                "--spring.profiles.active=prod",
                "--server.port=0",
                "--spring.datasource.url=" + url,
                "--spring.datasource.username=" + MySqlTestDatabase.username(),
                "--spring.datasource.password=" + MySqlTestDatabase.password(),
                "--spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
                "--agentsquad.delivery.enabled=false");
    }

    private static String messageChain(Throwable throwable) {
        StringBuilder builder = new StringBuilder();
        Throwable current = throwable;
        while (current != null) {
            builder.append(current.getMessage()).append('\n');
            current = current.getCause();
        }
        return builder.toString();
    }

    private static int countTables(String url) throws Exception {
        try (Connection connection = DriverManager.getConnection(url, MySqlTestDatabase.username(),
                MySqlTestDatabase.password())) {
            String schema = connection.getCatalog();
            try (var statement = connection.prepareStatement(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = ?")) {
                statement.setString(1, schema);
                try (var result = statement.executeQuery()) {
                    return result.next() ? result.getInt(1) : 0;
                }
            }
        }
    }
}
