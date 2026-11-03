package com.agentsquad.squad.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 会话策略 HTTP 验证：Secure Cookie、no-store、过期与账号停用始终 401。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SessionPolicyHttpTest {

    private static final String ADMIN_ID = "66666666-6666-4666-8666-666666666666";
    private static final String DB_URL;
    private static final ObjectMapper JSON = new ObjectMapper();

    static {
        DB_URL = GatewayTestDatabase.create("session-policy");
        GatewayTestDatabase.seedUser(DB_URL, ADMIN_ID, "policy-admin", "policy-password", "ADMIN", false);
    }

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> DB_URL);
        registry.add("spring.datasource.username", GatewayTestDatabase::username);
        registry.add("spring.datasource.password", GatewayTestDatabase::password);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("agentsquad.auth.secure-cookie", () -> "true");
        registry.add("agentsquad.auth.cleanup-initial-delay-ms", () -> "3600000");
    }

    @BeforeEach
    void resetAuthenticationState() {
        GatewayTestDatabase.clearAuthenticationState(DB_URL);
    }

    @Test
    void issueSecureNoStoreCookieAndRejectExpiredOrDisabledSessions() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> login = client.send(HttpRequest.newBuilder(
                                URI.create(base() + "/api/v1/auth/login"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"username\":\"policy-admin\",\"password\":\"policy-password\"}")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, login.statusCode());
        String setCookie = login.headers().firstValue("Set-Cookie").orElseThrow();
        assertTrue(setCookie.contains("HttpOnly"));
        assertTrue(setCookie.contains("Secure"));
        assertTrue(setCookie.contains("SameSite=Lax"));
        assertTrue(setCookie.contains("Path=/"));
        assertTrue(login.headers().firstValue("Cache-Control").orElse("").contains("no-store"));
        String cookie = setCookie.split(";", 2)[0];
        String csrf = JSON.readTree(login.body()).get("csrfToken").asText();

        assertEquals(401, myStatus(client, cookie), "CSRF 不匹配的写请求必须 401");
        HttpResponse<String> me = get(client, "/api/v1/auth/me", cookie);
        assertEquals(200, me.statusCode());
        assertTrue(me.headers().firstValue("Cache-Control").orElse("").contains("no-store"));

        execute("UPDATE sq_auth_session SET absolute_expires_at = DATE_SUB(NOW(3), INTERVAL 1 MINUTE)");
        assertEquals(401, myStatus(client, cookie));

        HttpResponse<String> second = client.send(HttpRequest.newBuilder(
                                URI.create(base() + "/api/v1/auth/login"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"username\":\"policy-admin\",\"password\":\"policy-password\"}")).build(),
                HttpResponse.BodyHandlers.ofString());
        String secondCookie = second.headers().firstValue("Set-Cookie").orElseThrow().split(";", 2)[0];
        assertEquals(200, get(client, "/api/v1/auth/me", secondCookie).statusCode());

        execute("UPDATE sq_user SET status = 'DISABLED' WHERE user_id = '" + ADMIN_ID + "'");
        assertEquals(401, get(client, "/api/v1/auth/me", secondCookie).statusCode());
        execute("UPDATE sq_user SET status = 'ACTIVE' WHERE user_id = '" + ADMIN_ID + "'");
    }

    private int myStatus(HttpClient client, String cookie) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(base() + "/api/v1/auth/logout"))
                        .header("Cookie", cookie).header("X-CSRF-Token", "mismatched-token")
                        .POST(HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString()).statusCode();
    }

    private HttpResponse<String> get(HttpClient client, String path, String cookie) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(base() + path))
                        .header("Cookie", cookie).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private void execute(String sql) throws Exception {
        try (Connection connection = DriverManager.getConnection(DB_URL, GatewayTestDatabase.username(),
                GatewayTestDatabase.password());
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private String base() {
        return "http://127.0.0.1:" + port;
    }
}
