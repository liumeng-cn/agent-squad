package com.agentsquad.squad.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 双网关实例共享 MySQL 会话验证。
 *
 * <p>两个真实 Netty 网关进程内实例连接同一隔离库：实例 A 登录的会话可在实例 B 使用，
 * 在实例 B 退出后实例 A 立即失效；证明会话权威在数据库而非单机内存。</p>
 */
class CrossInstanceSessionTest {

    private static final String ADMIN_ID = "77777777-7777-4777-8777-777777777777";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String DB_URL;
    private static ConfigurableApplicationContext first;
    private static ConfigurableApplicationContext second;

    static {
        DB_URL = GatewayTestDatabase.create("cross-instance");
        GatewayTestDatabase.seedUser(DB_URL, ADMIN_ID, "cross-admin", "cross-admin-password", "ADMIN", false);
    }

    @BeforeAll
    static void startInstances() {
        first = startInstance();
        second = startInstance();
    }

    @AfterAll
    static void stopInstances() {
        if (first != null) {
            first.close();
        }
        if (second != null) {
            second.close();
        }
    }

    @Test
    void shareSessionAndRevokeAcrossInstances() throws Exception {
        GatewayTestDatabase.clearAuthenticationState(DB_URL);
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> login = client.send(HttpRequest.newBuilder(
                                URI.create(base(first) + "/api/v1/auth/login"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"username\":\"cross-admin\",\"password\":\"cross-admin-password\"}")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, login.statusCode());
        String cookie = login.headers().firstValue("Set-Cookie").orElseThrow().split(";", 2)[0];
        String csrf = JSON.readTree(login.body()).get("data").get("csrfToken").asText();

        assertEquals(200, client.send(HttpRequest.newBuilder(URI.create(base(second) + "/api/v1/auth/me"))
                        .header("Cookie", cookie).GET().build(),
                HttpResponse.BodyHandlers.ofString()).statusCode());

        assertEquals(200, client.send(HttpRequest.newBuilder(URI.create(base(second) + "/api/v1/auth/logout"))
                        .header("Cookie", cookie).header("X-CSRF-Token", csrf)
                        .POST(HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString()).statusCode());

        assertEquals(401, client.send(HttpRequest.newBuilder(URI.create(base(first) + "/api/v1/auth/me"))
                        .header("Cookie", cookie).GET().build(),
                HttpResponse.BodyHandlers.ofString()).statusCode());
    }

    private static ConfigurableApplicationContext startInstance() {
        return new SpringApplicationBuilder(GatewayApplication.class).run(
                "--server.port=0",
                "--spring.datasource.url=" + DB_URL,
                "--spring.datasource.username=" + GatewayTestDatabase.username(),
                "--spring.datasource.password=" + GatewayTestDatabase.password(),
                "--spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
                "--gateway.business-service-url=http://127.0.0.1:9",
                "--agentsquad.auth.cleanup-initial-delay-ms=3600000");
    }

    private static String base(ConfigurableApplicationContext context) {
        return "http://127.0.0.1:" + ((WebServerApplicationContext) context).getWebServer().getPort();
    }
}
