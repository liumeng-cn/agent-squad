package com.agentsquad.squad.testsupport;

import com.agentsquad.squad.AgentSquadApplication;
import com.agentsquad.squad.config.SchemaVersionGate;
import com.agentsquad.squad.mapper.SchemaInspectionMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
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
        String url = PostgresTestDatabase.emptyUrl(Path.of("target", "schema-gate", "empty"));
        try (Connection connection = DriverManager.getConnection(url, PostgresTestDatabase.username(),
                PostgresTestDatabase.password()); Statement statement = connection.createStatement()) {
            // 框架启动前就要求这三张 AgentScope 表存在；平台业务表仍应为空，用于验证门禁失败时不写入平台 DDL。
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS agentscope_store ("
                    + "namespace_path VARCHAR(2048) NOT NULL, item_key VARCHAR(255) NOT NULL, "
                    + "value_json TEXT NOT NULL, version BIGINT NOT NULL, updated_at BIGINT NOT NULL, "
                    + "PRIMARY KEY (namespace_path, item_key))");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS agentscope_sessions ("
                    + "session_id VARCHAR(255) NOT NULL, state_key VARCHAR(255) NOT NULL, "
                    + "item_index INT NOT NULL DEFAULT 0, state_data TEXT NOT NULL, "
                    + "version BIGINT NOT NULL DEFAULT 0, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                    + "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                    + "PRIMARY KEY (session_id, state_key, item_index))");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS agentscope_snapshots ("
                    + "snapshot_id VARCHAR(512) NOT NULL PRIMARY KEY, data BYTEA NOT NULL, "
                    + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        }

        Exception failure = assertThrows(Exception.class, () -> start(url));

        assertTrue(messageChain(failure).contains("生产启动要求已执行版本化迁移"), messageChain(failure));
        assertEquals(0, countBusinessTables(url), "生产启动失败时不得写入任何平台业务表 DDL");
    }

    @Test
    void startsWithoutDdlWhenMigrationVersionApplied() throws Exception {
        String url = PostgresTestDatabase.emptyUrl(Path.of("target", "schema-gate", "applied"));
        try (Connection connection = DriverManager.getConnection(url, PostgresTestDatabase.username(),
                PostgresTestDatabase.password());
             Statement statement = connection.createStatement()) {
            statement.execute(new ClassPathResource("db/schema-postgresql.sql")
                    .getContentAsString(StandardCharsets.UTF_8));
            statement.executeUpdate("CREATE TABLE flyway_schema_history ("
                    + "installed_rank int NOT NULL PRIMARY KEY, version varchar(50), "
                    + "description varchar(200) NOT NULL, type varchar(20) NOT NULL, "
                    + "script varchar(1000) NOT NULL, checksum int, installed_by varchar(100) NOT NULL, "
                    + "installed_on timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                    + "execution_time int NOT NULL, success boolean NOT NULL)");
            statement.executeUpdate("INSERT INTO flyway_schema_history (installed_rank, version, description, "
                    + "type, script, installed_by, execution_time, success) VALUES "
                    + "(1, '1', 'initial', 'SQL', 'V1__initial_44_tables.sql', 'test', 0, true), "
                    + "(2, '2', 'multiuser identity', 'SQL', 'V2__multiuser_identity.sql', 'test', 0, true), "
                    + "(3, '3', 'admission', 'SQL', 'V3__runtime_admission.sql', 'test', 0, true), "
                    + "(4, '4', 'run identity', 'SQL', 'V4__admission_run_identity.sql', 'test', 0, true), "
                    + "(5, '5', 'final delivery', 'SQL', 'V5__final_delivery.sql', 'test', 0, true), "
                    + "(6, '6', 'event inbox', 'SQL', 'V6__runtime_event_inbox.sql', 'test', 0, true), "
                    + "(7, '7', 'natural reply', 'SQL', 'V7__natural_reply_protocol.sql', 'test', 0, true), "
                    + "(8, '8', 'model choice', 'SQL', 'V8__accepted_model_selection.sql', 'test', 0, true), "
                    + "(9, '9', 'execution activity', 'SQL', 'V9__execution_activity.sql', 'test', 0, true), "
                    + "(10, '10', 'execution trace', 'SQL', 'V10__execution_trace.sql', 'test', 0, true)");
        }

        try (ConfigurableApplicationContext context = start(url)) {
            assertTrue(context.isActive(), "版本已应用时生产上下文应正常启动");
        }
        assertEquals(54, countTables(url), "生产启动只校验版本，不得创建额外业务表");
        try (Connection connection = DriverManager.getConnection(url, PostgresTestDatabase.username(),
                PostgresTestDatabase.password()); Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM flyway_schema_history WHERE version = '3'");
        }
        Exception missingVersion = assertThrows(Exception.class, () -> start(url));
        assertTrue(messageChain(missingVersion).contains("生产数据库迁移版本落后"));
        assertEquals(54, countTables(url), "中间版本缺失时也不能执行 DDL");
    }

    @Test
    void mapperChecksKeepMissingTablesAndLegacyDeliveryFailClosed() {
        SchemaInspectionMapper mapper = mock(SchemaInspectionMapper.class);
        SchemaVersionGate gate = new SchemaVersionGate();
        ReflectionTestUtils.setField(gate, "schemaInspection", mapper);
        ReflectionTestUtils.setField(gate, "expectedVersion", 9);
        ReflectionTestUtils.setField(gate, "sqlInitMode", "never");
        ReflectionTestUtils.setField(gate, "strictIdentity", true);
        ReflectionTestUtils.setField(gate, "enforceUserStatus", true);
        when(mapper.countAppliedVersions(9)).thenReturn(9);
        when(mapper.countRequiredTables(anyList())).thenReturn(6);
        assertTrue(assertThrows(IllegalStateException.class, gate::afterPropertiesSet)
                .getMessage().contains("缺少必需业务表"));
        when(mapper.countRequiredTables(anyList())).thenReturn(9);
        when(mapper.countPendingWithoutDelivery()).thenReturn(1L);
        assertTrue(assertThrows(IllegalStateException.class, gate::afterPropertiesSet)
                .getMessage().contains("缺少最终交付权"));
        when(mapper.countPendingWithoutDelivery()).thenReturn(0L);
        gate.afterPropertiesSet();
    }

    private ConfigurableApplicationContext start(String url) {
        return SpringApplication.run(AgentSquadApplication.class,
                "--spring.profiles.active=prod",
                "--server.port=0",
                "--spring.datasource.url=" + url,
                "--spring.datasource.username=" + PostgresTestDatabase.username(),
                "--spring.datasource.password=" + PostgresTestDatabase.password(),
                "--spring.datasource.driver-class-name=org.postgresql.Driver",
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
        try (Connection connection = DriverManager.getConnection(url, PostgresTestDatabase.username(),
                PostgresTestDatabase.password());
             var statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = current_schema()");
             var result = statement.executeQuery()) {
            return result.next() ? result.getInt(1) : 0;
        }
    }

    private static int countBusinessTables(String url) throws Exception {
        try (Connection connection = DriverManager.getConnection(url, PostgresTestDatabase.username(),
                PostgresTestDatabase.password());
             var statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM information_schema.tables "
                             + "WHERE table_schema = current_schema() AND table_name LIKE 'sq\\_%'");
             var result = statement.executeQuery()) {
            return result.next() ? result.getInt(1) : 0;
        }
    }
}
