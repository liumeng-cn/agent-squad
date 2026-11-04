package com.agentsquad.squad.gateway;

import com.agentsquad.squad.bootstrap.BootstrapAdminApplication;
import com.agentsquad.squad.gateway.config.GatewayAuthConfiguration;
import com.agentsquad.squad.gateway.auth.AuthProperties;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;
import com.agentsquad.squad.gateway.admin.UserAccountService;
import com.agentsquad.squad.gateway.auth.AuthService;
import com.agentsquad.squad.common.exception.StateConflictException;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 初始管理员命令服务的验证：空表创建一次、密码不落明文、重复执行冲突、首次登录必须改密。
 */
@SpringBootTest(classes = BootstrapAdminApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class BootstrapAdminServiceTest {

    private static final String DB_URL = GatewayTestDatabase.create("bootstrap-admin");
    private static final String RAW_PASSWORD = "bootstrap-password";

    @Resource
    private UserAccountService userAccountService;

    @Resource
    private AuthService authService;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> DB_URL);
        registry.add("spring.datasource.username", GatewayTestDatabase::username);
        registry.add("spring.datasource.password", GatewayTestDatabase::password);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("agentsquad.auth.cleanup-initial-delay-ms", () -> "3600000");
    }

    @Test
    void productionRejectsAuthenticationDowngrade() {
        GatewayAuthConfiguration configuration = new GatewayAuthConfiguration();
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        ReflectionTestUtils.setField(configuration, "environment", environment);
        ReflectionTestUtils.setField(configuration, "authProperties", new AuthProperties());
        assertThrows(IllegalStateException.class, configuration::validateProductionAuthentication);
    }

    @Test
    void createInitialAdminOnlyOnceAndPersistDigest() throws Exception {
        String userId = userAccountService.bootstrapAdmin("Root-Admin", RAW_PASSWORD);
        assertTrue(userId != null && !userId.isBlank());

        try (Connection connection = DriverManager.getConnection(DB_URL, GatewayTestDatabase.username(),
                GatewayTestDatabase.password());
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                     "SELECT username, password_hash, role, status, must_change_password FROM sq_user")) {
            assertTrue(result.next());
            assertEquals("root-admin", result.getString("username"));
            assertEquals("ADMIN", result.getString("role"));
            assertEquals("ACTIVE", result.getString("status"));
            assertTrue(result.getBoolean("must_change_password"));
            String hash = result.getString("password_hash");
            assertFalse(hash.contains(RAW_PASSWORD));
            assertTrue(hash.startsWith("$2"));
            assertFalse(result.next(), "空表初始化只能创建一个管理员");
        }

        assertThrows(StateConflictException.class,
                () -> userAccountService.bootstrapAdmin("another-admin", RAW_PASSWORD));
        assertEquals(1, countUsers());

        String token = authService.login("root-admin", RAW_PASSWORD);
        var principal = authService.authenticate(token);
        assertTrue(principal.mustChangePassword());
        assertEquals(userId, principal.userId());
        assertEquals(userId, authService.authenticateWrite(token, principal.csrfToken()).userId());
        assertThrows(com.agentsquad.squad.common.exception.AuthenticationRequiredException.class,
                () -> authService.authenticateWrite(token, "invalid-csrf"));
        try (Connection connection = DriverManager.getConnection(DB_URL, GatewayTestDatabase.username(),
                GatewayTestDatabase.password());
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                     "SELECT created_at, last_seen_at FROM sq_auth_session")) {
            assertTrue(result.next());
            assertTrue(result.getTimestamp("created_at") != null);
            assertTrue(result.getTimestamp("last_seen_at") != null);
        }
        authService.logout(token);
        assertThrows(com.agentsquad.squad.common.exception.AuthenticationRequiredException.class,
                () -> authService.authenticate(token));
    }

    private int countUsers() throws Exception {
        try (Connection connection = DriverManager.getConnection(DB_URL, GatewayTestDatabase.username(),
                GatewayTestDatabase.password());
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM sq_user")) {
            result.next();
            return result.getInt(1);
        }
    }
}
