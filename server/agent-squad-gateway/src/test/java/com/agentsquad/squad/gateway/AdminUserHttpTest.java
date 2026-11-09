package com.agentsquad.squad.gateway;

import com.agentsquad.squad.service.UserAccountService;
import com.agentsquad.squad.exceptions.StateConflictException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 账号生命周期 HTTP 集成验证。
 *
 * <p>使用隔离 PostgreSQL 验证管理员列表/创建/更新/重置、最后管理员约束、CAS 冲突、
 * 角色变化撤销会话、临时密码限制与用户自行改密。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminUserHttpTest {

    private static final String ADMIN_ID = "3001";
    private static final String MEMBER_ID = "3002";
    private static final String DB_URL;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpServer UPSTREAM = startUpstream();

    static {
        DB_URL = GatewayTestDatabase.create("admin-user-http");
        GatewayTestDatabase.seedUser(DB_URL, Long.parseLong(ADMIN_ID), "admin", "admin-password", "ADMIN", false);
        GatewayTestDatabase.seedUser(DB_URL, Long.parseLong(MEMBER_ID), "member", "member-password", "USER", false);
    }

    @LocalServerPort
    private int port;

    @Resource
    private UserAccountService userAccountService;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("gateway.business-service-url", () -> "http://127.0.0.1:" + UPSTREAM.getAddress().getPort());
        registry.add("spring.datasource.url", () -> DB_URL);
        registry.add("spring.datasource.username", GatewayTestDatabase::username);
        registry.add("spring.datasource.password", GatewayTestDatabase::password);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("agentsquad.auth.max-failures", () -> "20");
        registry.add("agentsquad.auth.cleanup-initial-delay-ms", () -> "3600000");
    }

    private static HttpServer startUpstream() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/api/echo", exchange -> {
                byte[] bytes = "ok".getBytes();
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            });
            server.start();
            return server;
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    @AfterAll
    static void stop() {
        UPSTREAM.stop(0);
    }

    @BeforeEach
    void resetAuthenticationState() {
        GatewayTestDatabase.clearAuthenticationState(DB_URL);
    }

    @Test
    void manageUserLifecycleOverHttp() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        AdminSession admin = login(client, "admin", "admin-password");

        HttpResponse<String> list = get(client, "/api/v1/admin/users", admin);
        assertEquals(200, list.statusCode());
        assertTrue(list.headers().firstValue("Cache-Control").orElse("").contains("no-store"));
        JsonNode page = JSON.readTree(list.body()).get("data");
        assertEquals(ADMIN_ID, page.get("items").get(0).get("userId").asText());
        assertTrue(page.get("items").size() >= 2);

        HttpResponse<String> created = post(client, "/api/v1/admin/users",
                "{\"username\":\"new-user\",\"displayName\":\"新用户\",\"role\":\"USER\"}", admin);
        assertEquals(201, created.statusCode());
        JsonNode createdBody = JSON.readTree(created.body()).get("data");
        String newUserId = createdBody.get("userId").asText();
        String temporaryPassword = createdBody.get("temporaryPassword").asText();
        assertTrue(temporaryPassword.length() >= 20);
        assertFalse(created.body().contains("passwordHash"));

        assertEquals(409, code(post(client, "/api/v1/admin/users",
                "{\"username\":\"new-user\"}", admin)));
        assertEquals(422, code(post(client, "/api/v1/admin/users",
                "{\"username\":\"Bad Name!\"}", admin)));

        HttpResponse<String> updated = patch(client, "/api/v1/admin/users/" + newUserId,
                "{\"expectedVersion\":0,\"displayName\":\"新用户改名\"}", admin);
        assertEquals(200, updated.statusCode());
        assertEquals(1, JSON.readTree(updated.body()).get("data").get("version").asLong());
        assertEquals(409, code(patch(client, "/api/v1/admin/users/" + newUserId,
                "{\"expectedVersion\":0,\"displayName\":\"过期更新\"}", admin)));
        assertEquals(404, code(patch(client, "/api/v1/admin/users/not-exist",
                "{\"expectedVersion\":0,\"displayName\":\"x\"}", admin)));

        AdminSession newUser = login(client, "new-user", temporaryPassword);
        assertTrue(newUser.mustChangePassword());
        assertEquals(403, code(post(client, "/api/echo", "blocked", newUser)));
        assertEquals(403, code(get(client, "/api/v1/admin/users", newUser)));
        assertEquals(400, code(post(client, "/api/v1/auth/password",
                "{\"currentPassword\":\"wrong-password\",\"newPassword\":\"brand-new-password\"}",
                newUser)));
        assertEquals(0, code(post(client, "/api/v1/auth/password",
                "{\"currentPassword\":\"" + temporaryPassword + "\",\"newPassword\":\"brand-new-password\"}",
                newUser)));
        assertEquals(401, code(get(client, "/api/v1/auth/me", newUser)));

        AdminSession changed = login(client, "new-user", "brand-new-password");
        assertFalse(changed.mustChangePassword());
        assertEquals(200, post(client, "/api/echo", "passes-gateway", changed).statusCode());

        HttpResponse<String> reset = post(client,
                "/api/v1/admin/users/" + newUserId + "/reset-password", "", admin);
        assertEquals(200, reset.statusCode());
        String resetPassword = JSON.readTree(reset.body()).get("data").get("temporaryPassword").asText();
        assertEquals(401, loginCode(client, "new-user", "brand-new-password"));
        assertTrue(login(client, "new-user", resetPassword).mustChangePassword());
    }

    @Test
    void denyNonAdminAndMissingCsrf() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        AdminSession member = login(client, "member", "member-password");
        assertEquals(403, code(get(client, "/api/v1/admin/users", member)));
        assertEquals(403, code(post(client, "/api/v1/admin/users",
                "{\"username\":\"denied-user\"}", member)));

        AdminSession admin = login(client, "admin", "admin-password");
        HttpResponse<String> noCsrf = client.send(HttpRequest.newBuilder(
                                URI.create(base() + "/api/v1/admin/users"))
                        .header("Cookie", admin.cookie())
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{\"username\":\"no-csrf-user\"}")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(401, code(noCsrf));
    }

    @Test
    void protectLastActiveAdminAndRevokeOnRoleChange() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        AdminSession admin = login(client, "admin", "admin-password");

        assertEquals(409, code(patch(client, "/api/v1/admin/users/" + ADMIN_ID,
                "{\"expectedVersion\":0,\"status\":\"DISABLED\"}", admin)));

        AdminSession member = login(client, "member", "member-password");
        assertEquals(0, code(patch(client, "/api/v1/admin/users/" + MEMBER_ID,
                "{\"expectedVersion\":0,\"role\":\"ADMIN\"}", admin)));
        assertEquals(401, code(get(client, "/api/v1/auth/me", member)));

        assertEquals(0, code(patch(client, "/api/v1/admin/users/" + MEMBER_ID,
                "{\"expectedVersion\":1,\"status\":\"DISABLED\"}", admin)));
        assertEquals(409, code(patch(client, "/api/v1/admin/users/" + ADMIN_ID,
                "{\"expectedVersion\":0,\"status\":\"DISABLED\"}", admin)));
    }

    @Test
    void rejectsInvalidEnumValues() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        AdminSession admin = login(client, "admin", "admin-password");

        assertEquals(400, post(client, "/api/v1/admin/users",
                "{\"username\":\"bad-role\",\"role\":\"SUPERADMIN\"}", admin).statusCode());

        HttpResponse<String> created = post(client, "/api/v1/admin/users",
                "{\"username\":\"enum-test\",\"role\":\"USER\"}", admin);
        assertEquals(201, created.statusCode());
        String userId = JSON.readTree(created.body()).get("data").get("userId").asText();

        assertEquals(400, patch(client, "/api/v1/admin/users/" + userId,
                "{\"expectedVersion\":0,\"status\":\"LOCKED\"}", admin).statusCode());
        assertEquals(400, patch(client, "/api/v1/admin/users/" + userId,
                "{\"expectedVersion\":0,\"role\":\"GUEST\"}", admin).statusCode());
    }

    @Test
    void rejectBootstrapWhenUsersExist() {
        assertThrows(StateConflictException.class,
                () -> userAccountService.bootstrapAdmin("late-admin", "late-admin-password"));
    }

    private AdminSession login(HttpClient client, String username, String password) throws Exception {
        HttpResponse<String> response = client.send(HttpRequest.newBuilder(
                                URI.create(base() + "/api/v1/auth/login"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        JsonNode body = JSON.readTree(response.body()).get("data");
        String cookie = response.headers().firstValue("Set-Cookie").orElseThrow().split(";", 2)[0];
        return new AdminSession(cookie, body.get("csrfToken").asText(),
                body.get("mustChangePassword").asBoolean());
    }

    private int loginCode(HttpClient client, String username, String password) throws Exception {
        return code(client.send(HttpRequest.newBuilder(URI.create(base() + "/api/v1/auth/login"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}")).build(),
                HttpResponse.BodyHandlers.ofString()));
    }

    /**
     * 网关业务拒绝统一返回 HTTP 200，结果以响应体 code 判定。
     */
    private static int code(HttpResponse<String> response) throws Exception {
        assertEquals(200, response.statusCode(), response.body());
        return JSON.readTree(response.body()).get("code").asInt();
    }

    private HttpResponse<String> get(HttpClient client, String path, AdminSession session) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(base() + path))
                        .header("Cookie", session.cookie()).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(HttpClient client, String path, String body, AdminSession session)
            throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(base() + path))
                        .header("Cookie", session.cookie())
                        .header("X-CSRF-Token", session.csrf())
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> patch(HttpClient client, String path, String body, AdminSession session)
            throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(base() + path))
                        .header("Cookie", session.cookie())
                        .header("X-CSRF-Token", session.csrf())
                        .header("Content-Type", "application/json")
                        .method("PATCH", HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private String base() {
        return "http://127.0.0.1:" + port;
    }
}
