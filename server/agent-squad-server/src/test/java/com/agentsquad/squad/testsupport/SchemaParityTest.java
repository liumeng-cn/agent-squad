package com.agentsquad.squad.testsupport;

import org.junit.jupiter.api.Test;
import com.agentsquad.squad.config.SchemaVersionGate;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 开发 baseline 与生产版本迁移的结构一致性校验。
 * <p>在隔离 MySQL schema 上分别执行开发单文件 baseline 与 deploy 下活动表增量迁移，
 * 比较新建与当前版本升级后的表、列和索引；防止两套 DDL 漂移。不读取或修改开发库。</p>
 */
class SchemaParityTest {

    private static final String DEV_LABEL = "dev-baseline";
    private static final String PROD_LABEL = "prod-v1-v6";

    @Test
    void productionRejectsDdlAndUnsignedIdentityBeforeDatabaseAccess() {
        SchemaVersionGate gate = new SchemaVersionGate();
        ReflectionTestUtils.setField(gate, "sqlInitMode", "always");
        assertThrows(IllegalStateException.class, gate::afterPropertiesSet);
        ReflectionTestUtils.setField(gate, "sqlInitMode", "never");
        ReflectionTestUtils.setField(gate, "strictIdentity", true);
        ReflectionTestUtils.setField(gate, "enforceUserStatus", true);
        ReflectionTestUtils.setField(gate, "allowUnsignedLocal", true);
        assertThrows(IllegalStateException.class, gate::afterPropertiesSet);
    }

    @Test
    void devBaselineMatchesProductionMigrations() throws Exception {
        String devUrl = MySqlTestDatabase.url(Path.of("target", "schema-parity", DEV_LABEL));
        String prodUrl = MySqlTestDatabase.url(Path.of("target", "schema-parity", PROD_LABEL));

        try (Connection dev = DriverManager.getConnection(devUrl, MySqlTestDatabase.username(),
                MySqlTestDatabase.password());
             Connection prod = DriverManager.getConnection(prodUrl, MySqlTestDatabase.username(),
                     MySqlTestDatabase.password())) {
            ScriptUtils.executeSqlScript(dev, new ClassPathResource("db/migration/V1__baseline.sql"));

            Path migrationDir = locateMigrationDir();
            // 当前完整基线去掉本次新增表，构造升级前结构，再执行明确的增量DDL。
            String complete = new ClassPathResource("db/migration/V1__baseline.sql").getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
            String addition = Files.readString(migrationDir.resolve("V9__execution_activity.sql"));
            assertTrue(complete.contains(addition.trim()), "完整基线必须包含同一活动表定义");
            String previous = complete.replace(addition.trim(), "");
            ScriptUtils.executeSqlScript(prod, new org.springframework.core.io.ByteArrayResource(previous.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            ScriptUtils.executeSqlScript(prod, new FileSystemResource(migrationDir.resolve("V9__execution_activity.sql")));

            Map<String, Map<String, String>> devSchema = readSchema(dev);
            Map<String, Map<String, String>> prodSchema = readSchema(prod);

            assertEquals(53, devSchema.size(), "开发 baseline 应包含 53 张表");
            assertEquals(53, prodSchema.size(), "生产活动表升级 应包含 53 张表");
            assertEquals(devSchema.keySet(), prodSchema.keySet(), "两套 DDL 的表集合不一致");
            devSchema.forEach((table, devDefinition) -> {
                Map<String, String> prodDefinition = prodSchema.get(table);
                assertEquals(devDefinition, prodDefinition,
                        "表结构漂移: " + table + "\n开发: " + devDefinition + "\n生产: " + prodDefinition);
            });
        }
    }

    private static Path locateMigrationDir() {
        Path current = Path.of("").toAbsolutePath();
        for (int depth = 0; depth < 4 && current != null; depth++) {
            Path candidate = current.resolve("deploy").resolve("db").resolve("migrations");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("未找到 deploy/db/migrations 目录");
    }

    private static Map<String, Map<String, String>> readSchema(Connection connection) throws SQLException {
        String schema = connection.getCatalog();
        Map<String, Map<String, String>> tables = new LinkedHashMap<>();
        for (String table : listTables(connection, schema)) {
            Map<String, String> definition = new LinkedHashMap<>();
            definition.put("columns", String.join(";", listColumns(connection, schema, table)));
            definition.put("indexes", String.join(";", listIndexes(connection, schema, table)));
            tables.put(table, definition);
        }
        return tables;
    }

    private static List<String> listTables(Connection connection, String schema) throws SQLException {
        List<String> tables = new ArrayList<>();
        String sql = "SELECT table_name FROM information_schema.tables "
                + "WHERE table_schema = ? AND table_type = 'BASE TABLE' ORDER BY table_name";
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, schema);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    tables.add(result.getString(1));
                }
            }
        }
        return tables;
    }

    private static List<String> listColumns(Connection connection, String schema, String table)
            throws SQLException {
        List<String> columns = new ArrayList<>();
        String sql = "SELECT column_name, column_type, is_nullable, column_default, extra, column_comment "
                + "FROM information_schema.columns WHERE table_schema = ? AND table_name = ? "
                + "ORDER BY ordinal_position";
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, schema);
            statement.setString(2, table);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    columns.add(result.getString(1) + "|" + result.getString(2) + "|" + result.getString(3)
                            + "|" + String.valueOf(result.getString(4)) + "|" + result.getString(5)
                            + "|" + String.valueOf(result.getString(6)));
                }
            }
        }
        return columns;
    }

    private static List<String> listIndexes(Connection connection, String schema, String table)
            throws SQLException {
        List<String> indexes = new ArrayList<>();
        String sql = "SELECT index_name, non_unique, seq_in_index, column_name "
                + "FROM information_schema.statistics WHERE table_schema = ? AND table_name = ? "
                + "ORDER BY index_name, seq_in_index";
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, schema);
            statement.setString(2, table);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    indexes.add(result.getString(1) + "|" + result.getInt(2) + "|" + result.getInt(3)
                            + "|" + result.getString(4));
                }
            }
        }
        return indexes;
    }
}
