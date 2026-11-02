package com.agentsquad.squad.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * 生产启动的数据库结构门禁。
 *
 * <p>生产禁止服务启动执行 DDL，只校验版本化迁移已应用到期望版本，且身份表存在。
 * 校验失败直接终止启动，避免在不完整结构上对外提供服务。</p>
 */
@Slf4j
@Component
@Profile("prod")
public class SchemaVersionGate implements InitializingBean {

    private static final String HISTORY_TABLE = "flyway_schema_history";
    private static final List<String> REQUIRED_TABLES = List.of(
            "sq_user", "sq_auth_session", "sq_auth_rate_bucket", "sq_auth_audit");

    @Resource
    private DataSource dataSource;

    @Value("${agentsquad.schema.expected-version:2}")
    private int expectedVersion;

    @Override
    public void afterPropertiesSet() {
        try (Connection connection = dataSource.getConnection()) {
            int appliedVersion = readAppliedVersion(connection);
            if (appliedVersion < expectedVersion) {
                throw new IllegalStateException("生产数据库迁移版本落后: applied=" + appliedVersion
                        + ", expected=" + expectedVersion + "；请先执行 deploy/db/migrations 迁移");
            }
            int present = countRequiredTables(connection);
            if (present != REQUIRED_TABLES.size()) {
                throw new IllegalStateException("生产数据库缺少身份表: present=" + present
                        + "/" + REQUIRED_TABLES.size() + "；请先执行 V2__multiuser_identity.sql");
            }
            log.info("生产数据库结构校验通过，迁移版本 {}", appliedVersion);
        } catch (SQLException exception) {
            throw new IllegalStateException("生产数据库结构校验失败，请确认迁移已执行且账号具备只读校验权限", exception);
        }
    }

    private int readAppliedVersion(Connection connection) throws SQLException {
        String sql = "SELECT MAX(CAST(version AS UNSIGNED)) FROM " + HISTORY_TABLE + " WHERE success = 1";
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            if (!result.next()) {
                return -1;
            }
            int version = result.getInt(1);
            return result.wasNull() ? -1 : version;
        } catch (SQLException exception) {
            throw new IllegalStateException("生产启动要求已执行版本化迁移（缺少 " + HISTORY_TABLE + "）", exception);
        }
    }

    private int countRequiredTables(Connection connection) throws SQLException {
        String placeholders = String.join(",", REQUIRED_TABLES.stream().map(name -> "?").toList());
        String sql = "SELECT COUNT(*) FROM information_schema.tables "
                + "WHERE table_schema = DATABASE() AND table_name IN (" + placeholders + ")";
        try (var statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < REQUIRED_TABLES.size(); index++) {
                statement.setString(index + 1, REQUIRED_TABLES.get(index));
            }
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getInt(1) : 0;
            }
        }
    }
}
