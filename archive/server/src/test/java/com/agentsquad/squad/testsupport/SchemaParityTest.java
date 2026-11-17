package com.agentsquad.squad.testsupport;

import com.agentsquad.squad.config.SchemaVersionGate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
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
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * PostgreSQL 单一基线的结构稳定性校验。
 * <p>在同一隔离库重复执行完整基线，比较表、列与索引签名；确认 DDL 幂等，
 * 不会在重复部署时悄悄改变既有结构。</p>
 */
class SchemaParityTest {

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
    void reapplyingBaselineKeepsIdenticalStructure(@TempDir Path directory) throws Exception {
        String url = PostgresTestDatabase.url(directory);
        String script = new ClassPathResource("db/schema-postgresql.sql").getContentAsString(StandardCharsets.UTF_8);
        try (Connection connection = DriverManager.getConnection(url, PostgresTestDatabase.username(),
                PostgresTestDatabase.password())) {
            Map<String, Map<String, String>> first = readSchema(connection);
            try (var statement = connection.createStatement()) {
                statement.execute(script);
            }
            Map<String, Map<String, String>> second = readSchema(connection);

            assertEquals(53, first.size(), "PostgreSQL 基线应包含 53 张表");
            assertEquals(first.keySet(), second.keySet(), "重复执行后表集合发生变化");
            first.forEach((table, definition) -> assertEquals(definition, second.get(table),
                    "重复执行后表结构发生变化: " + table));
        }
    }

    private static Map<String, Map<String, String>> readSchema(Connection connection) throws SQLException {
        Map<String, Map<String, String>> tables = new LinkedHashMap<>();
        for (String table : listTables(connection)) {
            Map<String, String> definition = new LinkedHashMap<>();
            definition.put("columns", String.join(";", listColumns(connection, table)));
            definition.put("indexes", String.join(";", listIndexes(connection, table)));
            tables.put(table, definition);
        }
        return tables;
    }

    private static List<String> listTables(Connection connection) throws SQLException {
        List<String> tables = new ArrayList<>();
        String sql = "SELECT table_name FROM information_schema.tables "
                + "WHERE table_schema = current_schema() AND table_type = 'BASE TABLE' ORDER BY table_name";
        try (var statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                tables.add(result.getString(1));
            }
        }
        return tables;
    }

    private static List<String> listColumns(Connection connection, String table) throws SQLException {
        List<String> columns = new ArrayList<>();
        String sql = "SELECT column_name, data_type, is_nullable, column_default, "
                + "character_maximum_length, numeric_precision, numeric_scale "
                + "FROM information_schema.columns WHERE table_schema = current_schema() AND table_name = ? "
                + "ORDER BY ordinal_position";
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, table);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    columns.add(result.getString(1) + "|" + result.getString(2) + "|" + result.getString(3)
                            + "|" + result.getString(4) + "|" + result.getString(5)
                            + "|" + result.getString(6) + "|" + result.getString(7));
                }
            }
        }
        return columns;
    }

    private static List<String> listIndexes(Connection connection, String table) throws SQLException {
        List<String> indexes = new ArrayList<>();
        String sql = "SELECT indexname, indexdef FROM pg_indexes "
                + "WHERE schemaname = current_schema() AND tablename = ? ORDER BY indexname";
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, table);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    indexes.add(result.getString(1) + "|" + result.getString(2));
                }
            }
        }
        return indexes;
    }
}
