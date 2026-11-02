package com.agentsquad.squad.testsupport;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HexFormat;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 本地MySQL集成测试数据库分配器。
 * <p>
 * 每个临时目录映射独立schema，同目录重启复用持久事实；测试JVM退出时清理本次创建的as_test_前缀测试库，
 * 不触碰开发库。
 */
public final class MySqlTestDatabase {
    private static final ConcurrentMap<String, String> DATABASES = new ConcurrentHashMap<>();
    private static final Set<String> CREATED_SCHEMAS = ConcurrentHashMap.newKeySet();
    private static final AtomicReference<String> ADMIN_URL = new AtomicReference<>();

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(MySqlTestDatabase::dropCreatedSchemas,
                "mysql-test-schema-cleanup"));
    }

    private MySqlTestDatabase() {
    }

    /**
     * 通过显式管理员地址创建隔离schema；连接失败即失败，不回退到内存数据库。
     */
    public static String url(Path directory) {
        return DATABASES.computeIfAbsent(directory.toAbsolutePath().normalize().toString(), MySqlTestDatabase::create);
    }

    public static String username() {
        return System.getenv().getOrDefault("SQUAD_TEST_MYSQL_USER", "root");
    }

    public static String password() {
        String value = System.getenv("SQUAD_TEST_MYSQL_PASSWORD");
        if (value == null) {
            throw new IllegalStateException("MySQL集成测试需要SQUAD_TEST_MYSQL_PASSWORD，不读取应用私有配置");
        }
        return value;
    }

    private static String create(String identity) {
        try {
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(identity.getBytes(StandardCharsets.UTF_8))).substring(0, 24);
            String name = "as_test_" + digest;
            String admin = System.getenv().getOrDefault("SQUAD_TEST_MYSQL_ADMIN_URL",
                    "jdbc:mysql://127.0.0.1:13306/mysql?serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true");
            if (!admin.matches("jdbc:mysql://[^/]+/mysql(?:\\?.*)?")) {
                throw new IllegalStateException("测试管理员地址必须明确指向MySQL系统库mysql");
            }
            try (var connection = DriverManager.getConnection(admin, username(), password());
                 var statement = connection.createStatement()) {
                statement.executeUpdate("CREATE DATABASE IF NOT EXISTS `" + name + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_bin");
            }
            ADMIN_URL.compareAndSet(null, admin);
            CREATED_SCHEMAS.add(name);
            return admin.replaceFirst("/mysql(?=\\?|$)", "/" + name);
        } catch (Exception exception) {
            throw new IllegalStateException("无法创建隔离MySQL测试库，请检查本地MySQL和测试变量", exception);
        }
    }

    /**
     * 在测试JVM退出时删除本次创建的schema，避免按临时目录哈希建库导致长期累积。
     * 清理失败只提示不抛出，避免掩盖真实测试结果。
     */
    private static void dropCreatedSchemas() {
        String admin = ADMIN_URL.get();
        if (admin == null || CREATED_SCHEMAS.isEmpty()) {
            return;
        }
        try (Connection connection = DriverManager.getConnection(admin, username(), password())) {
            for (String name : CREATED_SCHEMAS) {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("DROP DATABASE IF EXISTS `" + name + "`");
                } catch (SQLException exception) {
                    System.err.println("清理MySQL测试库失败: " + name + " - " + exception.getMessage());
                }
            }
        } catch (Exception exception) {
            System.err.println("连接MySQL清理测试库失败: " + exception.getMessage());
        }
    }
}
