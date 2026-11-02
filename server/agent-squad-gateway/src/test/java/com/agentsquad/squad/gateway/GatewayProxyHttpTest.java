package com.agentsquad.squad.gateway;

import com.agentsquad.squad.gateway.auth.AuthService;
import com.agentsquad.squad.gateway.auth.LoginResult;
import com.agentsquad.squad.gateway.exception.AuthenticationRequiredException;
import com.agentsquad.squad.gateway.exception.RateLimitedException;
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
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 网关真实 HTTP 与持久会话验证。
 *
 * <p>使用隔离 MySQL 库验证登录、Cookie/CSRF、内部身份断言签发与伪造头清理、
 * multipart/SSE 字节保真、临时密码限制和登录限流；不依赖进程内账号或内存会话。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayProxyHttpTest {

    private static final String USER_ID = "11111111-1111-4111-8111-111111111111";
    private static final String TEMP_USER_ID = "22222222-2222-4222-8222-222222222222";
    private static final String DB_URL;
    private static final CountDownLatch RELEASE = new CountDownLatch(1);
    private static final HttpServer UPSTREAM = startUpstream();

    static {
        DB_URL = GatewayTestDatabase.create("proxy-http");
        GatewayTestDatabase.seedUser(DB_URL, USER_ID, "test", "test-password", "ADMIN", false);
        GatewayTestDatabase.seedUser(DB_URL, TEMP_USER_ID, "temp-user", "temp-password", "USER", true);
    }

    @LocalServerPort
    private int port;

    @Resource
    private AuthService authService;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("gateway.business-service-url", () -> "http://127.0.0.1:" + UPSTREAM.getAddress().getPort());
        registry.add("spring.datasource.url", () -> DB_URL);
        registry.add("spring.datasource.username", GatewayTestDatabase::username);
        registry.add("spring.datasource.password", GatewayTestDatabase::password);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("agentsquad.auth.max-failures", () -> "5");
        registry.add("agentsquad.auth.cleanup-initial-delay-ms", () -> "3600000");
    }

    @BeforeEach
    void resetAuthenticationState() {
        GatewayTestDatabase.clearAuthenticationState(DB_URL);
    }

    private static HttpServer startUpstream() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/api/echo", exchange -> {
                String body = exchange.getRequestHeaders().getFirst("X-User-Id") + ":"
                        + exchange.getRequestHeaders().getFirst("X-User-Role") + ":"
                        + exchange.getRequestHeaders().getFirst("X-Squad-Assertion") + ":"
                        + new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("X-Upstream", "echo");
                exchange.sendResponseHeaders(422, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            });
            server.createContext("/api/events", exchange -> {
                exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
                exchange.sendResponseHeaders(200, 0);
                exchange.getResponseBody().write("data: first\n\n".getBytes(StandardCharsets.UTF_8));
                exchange.getResponseBody().flush();
                try {
                    RELEASE.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
                exchange.close();
            });
            server.createContext("/api/redirect", exchange -> {
                exchange.getResponseHeaders().add("Location", "/api/echo");
                exchange.sendResponseHeaders(302, -1);
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
        RELEASE.countDown();
        UPSTREAM.stop(0);
    }

    @Test
    void authenticateLoginMeCsrfAndLogoutOverHttp() throws Exception {
        var client = HttpClient.newHttpClient();
        String base = "http://127.0.0.1:" + port;
        assertEquals(401, client.send(HttpRequest.newBuilder(URI.create(base + "/api/echo")).build(),
                HttpResponse.BodyHandlers.ofString()).statusCode());

        var login = login(client, base, "test", "test-password");
        assertEquals(200, login.statusCode());
        String setCookie = login.headers().firstValue("Set-Cookie").orElseThrow();
        assertTrue(setCookie.contains("HttpOnly"));
        assertFalse(setCookie.contains("Secure"), "本地 profile 不应强制 Secure");
        String cookie = setCookie.split(";", 2)[0];
        var json = new com.fasterxml.jackson.databind.ObjectMapper().readTree(login.body());
        String csrf = json.get("csrfToken").asText();
        assertEquals(USER_ID, json.get("userId").asText());
        assertFalse(json.get("mustChangePassword").asBoolean());

        var me = client.send(HttpRequest.newBuilder(URI.create(base + "/api/v1/auth/me"))
                .header("Cookie", cookie).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, me.statusCode());
        String refreshedCsrf = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(me.body()).get("csrfToken").asText();
        assertEquals(csrf, refreshedCsrf, "刷新后 /me 必须返回同一 CSRF，避免多标签页互相失效");
        assertEquals(401, client.send(HttpRequest.newBuilder(URI.create(base + "/api/echo"))
                        .header("Cookie", cookie).POST(HttpRequest.BodyPublishers.ofString("blocked")).build(),
                HttpResponse.BodyHandlers.ofString()).statusCode());
        assertEquals(204, client.send(HttpRequest.newBuilder(URI.create(base + "/api/v1/auth/logout"))
                .header("Cookie", cookie).header("X-CSRF-Token", refreshedCsrf)
                .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString()).statusCode());
        assertEquals(401, client.send(HttpRequest.newBuilder(URI.create(base + "/api/v1/auth/me"))
                .header("Cookie", cookie).build(), HttpResponse.BodyHandlers.ofString()).statusCode());
    }

    @Test
    void preserveStatusBodyAndOverrideIdentity() throws Exception {
        LoginResult login = loginByService();
        String token = login.token();
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/echo"))
                .header("Cookie", "SQUAD_SESSION=" + token).header("X-CSRF-Token", login.view().csrfToken())
                .header("X-User-Id", "forged").header("X-User-Role", "ADMIN")
                .header("X-Squad-Assertion", "forged")
                .POST(HttpRequest.BodyPublishers.ofString("payload")).build();
        var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(422, response.statusCode());
        assertEquals("echo", response.headers().firstValue("X-Upstream").orElseThrow());
        verifySignedBody(response.body(), "POST", "/api/echo", "payload");
    }

    @Test
    void preserveRawMultipartBytes() throws Exception {
        LoginResult login = loginByService();
        String payload = "--boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"sample.txt\"\r\n"
                + "Content-Type: text/plain\r\n\r\nfile content\r\n--boundary--\r\n";
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/echo"))
                .header("Cookie", "SQUAD_SESSION=" + login.token())
                .header("X-CSRF-Token", login.view().csrfToken())
                .header("Content-Type", "multipart/form-data; boundary=boundary")
                .POST(HttpRequest.BodyPublishers.ofString(payload)).build();
        var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(422, response.statusCode());
        verifySignedBody(response.body(), "POST", "/api/echo", payload);
    }

    @Test
    void preserveRedirectStatusAndLocation() throws Exception {
        LoginResult login = loginByService();
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/redirect"))
                .header("Cookie", "SQUAD_SESSION=" + login.token()).GET().build();
        var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(302, response.statusCode());
        assertEquals("/api/echo", response.headers().firstValue("Location").orElseThrow());
    }

    @Test
    void deliverFirstFrameBeforeUpstreamCompletes() throws Exception {
        LoginResult login = loginByService();
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/events"))
                .header("Cookie", "SQUAD_SESSION=" + login.token()).build();
        var future = CompletableFuture.supplyAsync(() -> {
            try {
                var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofInputStream());
                try (var body = response.body()) {
                    return new String(body.readNBytes(13), StandardCharsets.UTF_8);
                }
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        });
        try {
            assertEquals("data: first\n\n", future.get(4, TimeUnit.SECONDS));
            assertEquals(1, RELEASE.getCount());
        } finally {
            RELEASE.countDown();
        }
    }

    @Test
    void restrictBusinessCallsUntilTemporaryPasswordChanged() throws Exception {
        var client = HttpClient.newHttpClient();
        String base = "http://127.0.0.1:" + port;
        var login = login(client, base, "temp-user", "temp-password");
        assertEquals(200, login.statusCode());
        String cookie = login.headers().firstValue("Set-Cookie").orElseThrow().split(";", 2)[0];
        var json = new com.fasterxml.jackson.databind.ObjectMapper().readTree(login.body());
        assertTrue(json.get("mustChangePassword").asBoolean());

        assertEquals(200, client.send(HttpRequest.newBuilder(URI.create(base + "/api/v1/auth/me"))
                .header("Cookie", cookie).build(), HttpResponse.BodyHandlers.ofString()).statusCode());
        assertEquals(403, client.send(HttpRequest.newBuilder(URI.create(base + "/api/echo"))
                        .header("Cookie", cookie).header("X-CSRF-Token", json.get("csrfToken").asText())
                        .POST(HttpRequest.BodyPublishers.ofString("blocked")).build(),
                HttpResponse.BodyHandlers.ofString()).statusCode());
    }

    @Test
    void lockAccountAndSourceAfterRepeatedFailures() {
        for (int attempt = 0; attempt < 5; attempt++) {
            assertThrows(AuthenticationRequiredException.class,
                    () -> authService.login("test", "wrong-password", "lock-test-ip", "lock").block());
        }
        assertThrows(RateLimitedException.class,
                () -> authService.login("test", "test-password", "lock-test-ip", "lock").block());
        assertThrows(RateLimitedException.class,
                () -> authService.login("test", "wrong-password", "lock-test-ip", "lock").block());
    }

    @Test
    void persistOnlyTokenDigestAndSurvivingRows() throws Exception {
        LoginResult login = loginByService();
        assertEquals(USER_ID, authService.authenticate(login.token(), "persist").block().userId());
        try (Connection connection = DriverManager.getConnection(DB_URL, GatewayTestDatabase.username(),
                GatewayTestDatabase.password());
             var statement = connection.prepareStatement(
                     "SELECT token_hash, csrf_hash FROM sq_auth_session WHERE user_id = ?")) {
            statement.setString(1, USER_ID);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                assertEquals(64, result.getString("token_hash").length());
                assertFalse(result.getString("token_hash").equals(login.token()));
                assertEquals(64, result.getString("csrf_hash").length());
            }
        }
    }

    /**
     * 校验上游只收到签名断言、浏览器伪造的身份头已被清除，且断言绑定本次方法与路径。
     */
    private static String verifySignedBody(String body, String method, String path, String payload) throws Exception {
        String[] parts = body.split(":", 4);
        assertEquals(4, parts.length, "上游响应应包含 用户:角色:断言:请求体");
        assertEquals("null", parts[0], "网关必须移除浏览器伪造的 X-User-Id");
        assertEquals("null", parts[1], "网关必须移除浏览器伪造的 X-User-Role");
        assertEquals(payload, parts[3]);
        String[] segments = parts[2].split("\\.");
        assertEquals(2, segments.length, "断言应为 payload.signature");
        var claims = new ObjectMapper().readTree(Base64.getUrlDecoder().decode(segments[0]));
        assertEquals(USER_ID, claims.get("sub").asText());
        assertEquals("ADMIN", claims.get("roles").get(0).asText());
        assertEquals(method, claims.get("method").asText());
        assertEquals(path, claims.get("path").asText());
        assertFalse(claims.get("kid").asText().isBlank());
        assertTrue(claims.get("exp").asLong() > claims.get("iat").asLong(), "断言必须带有效期");
        return parts[2];
    }

    private LoginResult loginByService() {
        return authService.login("test", "test-password", "test-ip", "test").block();
    }

    private static HttpResponse<String> login(HttpClient client, String base, String username, String password)
            throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(base + "/api/v1/auth/login"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}")).build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
