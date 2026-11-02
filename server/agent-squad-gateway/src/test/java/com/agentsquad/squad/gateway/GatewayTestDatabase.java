package com.agentsquad.squad.gateway;

import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 网关测试的隔离 MySQL 库。
 *
 * <p>创建 as_test_ 前缀的独立 schema，执行多用户身份迁移并播种测试账号；
 * JVM 退出时只清理本次创建的测试库，不触碰开发库。</p>
 */
final class GatewayTestDatabase {

    private static final Set<String> CREATED = ConcurrentHashMap.newKeySet();
    private static final AtomicReference<String> ADMIN_URL = new AtomicReference<>();

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(GatewayTestDatabase::dropCreated,
                "gateway-test-schema-cleanup"));
    }

    private GatewayTestDatabase() {
    }

    static String create(String label) {
        String admin = System.getenv().getOrDefault("SQUAD_TEST_MYSQL_ADMIN_URL",
                "jdbc:mysql://127.0.0.1:13306/mysql?serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true");
        String name = "as_test_gw_" + Integer.toHexString(label.hashCode());
        try (Connection connection = DriverManager.getConnection(admin, username(), password());
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE DATABASE IF NOT EXISTS `" + name
                    + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_bin");
        } catch (SQLException exception) {
            throw new IllegalStateException("无法创建网关测试库，请检查 MySQL 测试变量", exception);
        }
        ADMIN_URL.compareAndSet(null, admin);
        CREATED.add(name);
        String url = admin.replaceFirst("/mysql(?=\\?|$)", "/" + name);
        applyMigration(url);
        return url;
    }

    static String username() {
        return System.getenv().getOrDefault("SQUAD_TEST_MYSQL_USER", "root");
    }

    static String password() {
        String value = System.getenv("SQUAD_TEST_MYSQL_PASSWORD");
        if (value == null) {
            throw new IllegalStateException("网关集成测试需要 SQUAD_TEST_MYSQL_PASSWORD");
        }
        return value;
    }

    static void seedUser(String url, String userId, String username, String rawPassword, String role,
                         boolean mustChangePassword) {
        String hash = new BCryptPasswordEncoder(4).encode(rawPassword);
        try (Connection connection = DriverManager.getConnection(url, username(), password());
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO sq_user (user_id, username, display_name, password_hash, role, status, "
                             + "auth_version, must_change_password, creator_id, creator_name) "
                             + "VALUES (?, ?, ?, ?, ?, 'ACTIVE', 0, ?, ?, ?)")) {
            statement.setString(1, userId);
            statement.setString(2, username);
            statement.setString(3, username);
            statement.setString(4, hash);
            statement.setString(5, role);
            statement.setBoolean(6, mustChangePassword);
            statement.setString(7, userId);
            statement.setString(8, username);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("播种网关测试账号失败: " + username, exception);
        }
    }

    static void clearAuthenticationState(String url) {
        try (Connection connection = DriverManager.getConnection(url, username(), password());
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM sq_auth_session");
            statement.executeUpdate("DELETE FROM sq_auth_rate_bucket");
            statement.executeUpdate("DELETE FROM sq_auth_audit");
        } catch (SQLException exception) {
            throw new IllegalStateException("清理网关测试认证状态失败", exception);
        }
    }

    private static void applyMigration(String url) {
        try (Connection connection = DriverManager.getConnection(url, username(), password())) {
            ScriptUtils.executeSqlScript(connection,
                    new org.springframework.core.io.FileSystemResource(migrationDir()
                            .resolve("V2__multiuser_identity.sql")));
        } catch (SQLException exception) {
            throw new IllegalStateException("执行网关测试迁移失败", exception);
        }
    }

    private static Path migrationDir() {
        Path current = Path.of("").toAbsolutePath();
        for (int depth = 0; depth < 4 && current != null; depth++) {
            Path candidate = current.resolve("deploy").resolve("db").resolve("migrations");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("未找到 deploy/db/migrations");
    }

    private static void dropCreated() {
        String admin = ADMIN_URL.get();
        if (admin == null) {
            return;
        }
        try (Connection connection = DriverManager.getConnection(admin, username(), password())) {
            for (String name : CREATED) {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("DROP DATABASE IF EXISTS `" + name + "`");
                } catch (SQLException exception) {
                    System.err.println("清理网关测试库失败: " + name + " - " + exception.getMessage());
                }
            }
        } catch (Exception exception) {
            System.err.println("连接 MySQL 清理网关测试库失败: " + exception.getMessage());
        }
    }
}
