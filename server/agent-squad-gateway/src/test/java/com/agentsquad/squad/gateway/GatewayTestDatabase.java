package com.agentsquad.squad.gateway;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.nio.charset.StandardCharsets;
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
 * 网关测试的隔离 PostgreSQL 库。
 *
 * <p>创建 as_test_ 前缀的独立数据库，加载唯一完整基线并播种测试账号；
 * JVM 退出时只清理本次创建的测试库，不触碰开发库。</p>
 */
final class GatewayTestDatabase {

    private static final Set<String> CREATED = ConcurrentHashMap.newKeySet();
    private static final AtomicReference<String> ADMIN_URL = new AtomicReference<>();

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(GatewayTestDatabase::dropCreated,
                "gateway-test-database-cleanup"));
    }

    private GatewayTestDatabase() {
    }

    static String create(String label) {
        String admin = System.getenv().getOrDefault("SQUAD_TEST_POSTGRES_ADMIN_URL",
                "jdbc:postgresql://127.0.0.1:15432/postgres");
        String name = "as_test_gw_" + Integer.toHexString(label.hashCode()) + "_"
                + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        try (Connection connection = DriverManager.getConnection(admin, username(), password());
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("DROP DATABASE IF EXISTS " + name + " WITH (FORCE)");
            statement.executeUpdate("CREATE DATABASE " + name);
        } catch (SQLException exception) {
            throw new IllegalStateException("无法创建网关测试库，请检查 PostgreSQL 测试变量", exception);
        }
        ADMIN_URL.compareAndSet(null, admin);
        CREATED.add(name);
        String url = admin.replaceFirst("/postgres(?=\\?|$)", "/" + name);
        // stringtype=unspecified 让字符串参数按目标列类型解析，JSON 列可直接接受 Mapper 传入的字符串。
        url = url + (url.contains("?") ? "&" : "?") + "stringtype=unspecified";
        applySchema(url, name);
        return url;
    }

    static String username() {
        return System.getenv().getOrDefault("SQUAD_TEST_POSTGRES_USER", "squad");
    }

    static String password() {
        String value = System.getenv("SQUAD_TEST_POSTGRES_PASSWORD");
        if (value == null) {
            throw new IllegalStateException("网关集成测试需要 SQUAD_TEST_POSTGRES_PASSWORD");
        }
        return value;
    }

    static void seedUser(String url, Long userId, String username, String rawPassword, String role,
                         boolean mustChangePassword) {
        String hash = new BCryptPasswordEncoder(4).encode(rawPassword);
        try (Connection connection = DriverManager.getConnection(url, username(), password());
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO sq_user (user_id, username, display_name, password_hash, role, status, "
                             + "auth_version, must_change_password, creator_id, creator_name) "
                             + "VALUES (?, ?, ?, ?, ?, 'ACTIVE', 0, ?, ?, ?)")) {
            statement.setLong(1, userId);
            statement.setString(2, username);
            statement.setString(3, username);
            statement.setString(4, hash);
            statement.setString(5, role);
            statement.setBoolean(6, mustChangePassword);
            statement.setLong(7, userId);
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

    /**
     * 只在本次刚创建的随机测试库加载结构并移除样例账号，禁止作用于开发库。
     */
    private static void applySchema(String url, String databaseName) {
        if (!databaseName.matches("as_test_gw_[a-f0-9]+_[a-f0-9]{12}") || !CREATED.contains(databaseName)) {
            throw new IllegalStateException("拒绝初始化非本次创建的网关测试库");
        }
        try (Connection connection = DriverManager.getConnection(url, username(), password())) {
            if (!databaseName.equals(connection.getCatalog())) {
                throw new IllegalStateException("网关测试连接与新建库不匹配");
            }
            try (Statement statement = connection.createStatement()) {
                // 基线包含 $$ 函数体，按整段脚本执行，避免按分号切分破坏 PL/pgSQL。
                statement.execute(Files.readString(schemaFile(), StandardCharsets.UTF_8));
                statement.executeUpdate("DELETE FROM sq_auth_session");
                statement.executeUpdate("DELETE FROM sq_auth_rate_bucket");
                statement.executeUpdate("DELETE FROM sq_auth_audit");
                statement.executeUpdate("DELETE FROM sq_user");
            }
        } catch (Exception exception) {
            throw new IllegalStateException("加载网关测试完整表结构失败", exception);
        }
    }

    private static Path schemaFile() {
        Path current = Path.of("").toAbsolutePath();
        for (int depth = 0; depth < 4 && current != null; depth++) {
            Path candidate = current.resolve("server/agent-squad-server/src/main/resources/db/schema-postgresql.sql");
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("未找到 PostgreSQL 完整基线 db/schema-postgresql.sql");
    }

    private static void dropCreated() {
        String admin = ADMIN_URL.get();
        if (admin == null) {
            return;
        }
        try (Connection connection = DriverManager.getConnection(admin, username(), password())) {
            for (String name : CREATED) {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("DROP DATABASE IF EXISTS " + name + " WITH (FORCE)");
                } catch (SQLException exception) {
                    System.err.println("清理网关测试库失败: " + name + " - " + exception.getMessage());
                }
            }
        } catch (Exception exception) {
            System.err.println("连接 PostgreSQL 清理网关测试库失败: " + exception.getMessage());
        }
    }
}
