package com.agentsquad.squad.testsupport;

import org.springframework.core.io.ClassPathResource;

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
 * 本地PostgreSQL集成测试数据库分配器。
 * <p>
 * 每个临时目录映射独立数据库，同目录重启复用其中的持久事实；测试JVM退出时清理本次创建的
 * as_test_前缀测试库，不触碰开发库。
 */
public final class PostgresTestDatabase {
    /** 与生产空库基线保持同一份 DDL，避免测试与运行结构分叉。 */
    private static final String SCHEMA_LOCATION = "db/schema-postgresql.sql";
    private static final ConcurrentMap<String, String> DATABASES = new ConcurrentHashMap<>();
    private static final Set<String> CREATED_DATABASES = ConcurrentHashMap.newKeySet();
    private static final AtomicReference<String> ADMIN_URL = new AtomicReference<>();

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(PostgresTestDatabase::dropCreatedDatabases,
                "postgres-test-database-cleanup"));
    }

    private PostgresTestDatabase() {
    }

    /**
     * 创建已建表的隔离测试库；连接失败即失败，不回退到内存数据库。
     */
    public static String url(Path directory) {
        String identity = directory.toAbsolutePath().normalize().toString();
        return DATABASES.computeIfAbsent(identity, PostgresTestDatabase::create);
    }

    /**
     * 只创建空库，供验证启动阶段不执行DDL的结构门禁测试使用。
     */
    public static String emptyUrl(Path directory) {
        String identity = directory.toAbsolutePath().normalize().toString();
        return DATABASES.computeIfAbsent("empty:" + identity, key -> createEmpty(identity));
    }

    public static String username() {
        return System.getenv().getOrDefault("SQUAD_TEST_POSTGRES_USER", "squad");
    }

    public static String password() {
        String value = System.getenv("SQUAD_TEST_POSTGRES_PASSWORD");
        if (value == null) {
            throw new IllegalStateException("PostgreSQL集成测试需要SQUAD_TEST_POSTGRES_PASSWORD，不读取应用私有配置");
        }
        return value;
    }

    private static String create(String identity) {
        String url = createEmpty(identity);
        try (Connection connection = DriverManager.getConnection(url, username(), password());
             Statement statement = connection.createStatement()) {
            // 基线包含 $$ 函数体，按整段脚本执行，避免按分号切分破坏 PL/pgSQL。
            statement.execute(new ClassPathResource(SCHEMA_LOCATION)
                    .getContentAsString(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("初始化PostgreSQL隔离测试库失败", exception);
        }
        return url;
    }

    private static String createEmpty(String identity) {
        try {
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(identity.getBytes(StandardCharsets.UTF_8))).substring(0, 24);
            String name = "as_test_" + digest;
            String admin = System.getenv().getOrDefault("SQUAD_TEST_POSTGRES_ADMIN_URL",
                    "jdbc:postgresql://127.0.0.1:15432/postgres");
            if (!admin.matches("jdbc:postgresql://[^/]+/postgres(?:\\?.*)?")) {
                throw new IllegalStateException("测试管理员地址必须明确指向PostgreSQL系统库postgres");
            }
            try (Connection connection = DriverManager.getConnection(admin, username(), password());
                 Statement statement = connection.createStatement()) {
                // 遗留的同名库来自异常退出的旧测试JVM，重建可避免半初始化结构影响本轮断言。
                statement.executeUpdate("DROP DATABASE IF EXISTS " + name + " WITH (FORCE)");
                statement.executeUpdate("CREATE DATABASE " + name);
            }
            ADMIN_URL.compareAndSet(null, admin);
            CREATED_DATABASES.add(name);
            String url = admin.replaceFirst("/postgres(?=\\?|$)", "/" + name);
            // stringtype=unspecified 让字符串参数按目标列类型解析，JSON 列可直接接受 Mapper 传入的字符串。
            return url + (url.contains("?") ? "&" : "?") + "stringtype=unspecified";
        } catch (Exception exception) {
            throw new IllegalStateException("无法创建隔离PostgreSQL测试库，请检查本地PostgreSQL和测试变量", exception);
        }
    }

    /**
     * 在测试JVM退出时删除本次创建的测试库；清理失败只提示不抛出，避免掩盖真实测试结果。
     */
    private static void dropCreatedDatabases() {
        String admin = ADMIN_URL.get();
        if (admin == null || CREATED_DATABASES.isEmpty()) {
            return;
        }
        try (Connection connection = DriverManager.getConnection(admin, username(), password())) {
            for (String name : CREATED_DATABASES) {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("DROP DATABASE IF EXISTS " + name + " WITH (FORCE)");
                } catch (SQLException exception) {
                    System.err.println("清理PostgreSQL测试库失败: " + name + " - " + exception.getMessage());
                }
            }
        } catch (Exception exception) {
            System.err.println("连接PostgreSQL清理测试库失败: " + exception.getMessage());
        }
    }
}
