package com.agentsquad.squad.gateway;

import com.sun.net.httpserver.HttpServer;
import jakarta.annotation.Resource;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import com.agentsquad.squad.gateway.auth.AuthService;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 网关真实 HTTP 代理验证。
 * <p>
 * 通过独立上游服务器验证身份覆盖、状态保留与上游结束前事件帧可见。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"agentsquad.auth.username=test", "agentsquad.auth.password=test"})
class GatewayProxyHttpTest {

    private static final CountDownLatch RELEASE = new CountDownLatch(1);
    private static final HttpServer UPSTREAM = startUpstream();

    @LocalServerPort
    private int port;

    @Resource
    private AuthService authService;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("gateway.business-service-url", () -> "http://127.0.0.1:" + UPSTREAM.getAddress().getPort());
    }

    private static HttpServer startUpstream() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/api/echo", exchange -> {
                String body = exchange.getRequestHeaders().getFirst("X-User-Id") + ":"
                        + exchange.getRequestHeaders().getFirst("X-User-Role") + ":"
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
        var login = client.send(HttpRequest.newBuilder(URI.create(base + "/api/v1/auth/login"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{\"username\":\"test\",\"password\":\"test\"}")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, login.statusCode());
        String cookie = login.headers().firstValue("Set-Cookie").orElseThrow().split(";", 2)[0];
        var json = new com.fasterxml.jackson.databind.ObjectMapper().readTree(login.body());
        String csrf = json.get("csrfToken").asText();
        assertEquals(200, client.send(HttpRequest.newBuilder(URI.create(base + "/api/v1/auth/me"))
                .header("Cookie", cookie).build(), HttpResponse.BodyHandlers.ofString()).statusCode());
        assertEquals(401, client.send(HttpRequest.newBuilder(URI.create(base + "/api/echo"))
                        .header("Cookie", cookie).POST(HttpRequest.BodyPublishers.ofString("blocked")).build(),
                HttpResponse.BodyHandlers.ofString()).statusCode());
        assertEquals(204, client.send(HttpRequest.newBuilder(URI.create(base + "/api/v1/auth/logout"))
                .header("Cookie", cookie).header("X-CSRF-Token", csrf)
                .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString()).statusCode());
        assertEquals(401, client.send(HttpRequest.newBuilder(URI.create(base + "/api/v1/auth/me"))
                .header("Cookie", cookie).build(), HttpResponse.BodyHandlers.ofString()).statusCode());
    }

    @Test
    void preserveStatusBodyAndOverrideIdentity() throws Exception {
        String token = authService.login("test", "test");
        String csrf = authService.authenticate(token).csrfToken();
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/echo"))
                .header("Cookie", "SQUAD_SESSION=" + token).header("X-CSRF-Token", csrf)
                .header("X-User-Id", "forged").header("X-User-Role", "forged")
                .POST(HttpRequest.BodyPublishers.ofString("payload")).build();
        var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(422, response.statusCode());
        assertEquals("echo", response.headers().firstValue("X-Upstream").orElseThrow());
        assertEquals(authService.authenticate(token).userId() + ":ADMIN:payload", response.body());
    }

    @Test
    void preserveRawMultipartBytes() throws Exception {
        String token = authService.login("test", "test");
        String payload = "--boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"sample.txt\"\r\n"
                + "Content-Type: text/plain\r\n\r\nfile content\r\n--boundary--\r\n";
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/echo"))
                .header("Cookie", "SQUAD_SESSION=" + token)
                .header("X-CSRF-Token", authService.authenticate(token).csrfToken())
                .header("Content-Type", "multipart/form-data; boundary=boundary")
                .POST(HttpRequest.BodyPublishers.ofString(payload)).build();
        var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(422, response.statusCode());
        assertEquals(authService.authenticate(token).userId() + ":ADMIN:" + payload, response.body());
    }

    @Test
    void deliverFirstFrameBeforeUpstreamCompletes() throws Exception {
        String token = authService.login("test", "test");
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/events"))
                .header("Cookie", "SQUAD_SESSION=" + token).build();
        var future = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
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
}
