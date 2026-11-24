package com.agentsquad.squad.v2;

import com.agentsquad.squad.common.exceptions.BusinessException;
import com.agentsquad.squad.v2.agent.tool.ConfiguredToolFactory;
import com.agentsquad.squad.v2.agent.tool.ConfiguredToolRunner;
import com.agentsquad.squad.v2.agent.tool.builtin.BuiltinToolRegistry;
import com.agentsquad.squad.v2.bo.HttpToolSpecBO;
import com.agentsquad.squad.v2.bo.KeyValueBO;
import com.agentsquad.squad.v2.bo.ToolDefinitionBO;
import com.agentsquad.squad.v2.bo.ToolRunResultBO;
import com.agentsquad.squad.v2.enums.HttpToolMethod;
import com.agentsquad.squad.v2.enums.ToolType;
import com.agentsquad.squad.v2.utils.ToolSchemaRules;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 配置化工具试运行测试
 * <p>
 * 用本地 HTTP 服务验证按配置生成的 HTTP 工具：参数校验、请求渲染、结果提取、错误转换与凭据遮蔽；
 * 同时验证内置工具按配置改名后仍调用原实现。
 */
class ConfiguredToolRunnerTest {
    private HttpServer server;

    private final AtomicReference<String> lastAuthorization = new AtomicReference<>();

    private final AtomicReference<String> lastQuery = new AtomicReference<>();

    private ConfiguredToolRunner runner;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/orders", exchange -> {
            lastAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            lastQuery.set(exchange.getRequestURI().getRawQuery());
            boolean missing = exchange.getRequestURI().getPath().endsWith("/404");
            byte[] body = (missing
                    ? "{\"message\":\"not found secret-value\"}"
                    : "{\"data\":{\"id\":\"42\",\"status\":\"PAID\"}}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(missing ? 404 : 200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        ConfiguredToolFactory factory = new ConfiguredToolFactory();
        ReflectionTestUtils.setField(factory, "builtinTools", new BuiltinToolRegistry());
        ReflectionTestUtils.setField(factory, "toolHttpClient", HttpClient.newHttpClient());
        runner = new ConfiguredToolRunner();
        ReflectionTestUtils.setField(runner, "toolFactory", factory);
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private ToolDefinitionBO httpTool(String pointer) {
        KeyValueBO authorization = new KeyValueBO();
        authorization.setName("Authorization");
        authorization.setValue("Bearer {{credential}}");

        HttpToolSpecBO spec = new HttpToolSpecBO();
        spec.setMethod(HttpToolMethod.GET);
        spec.setUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/orders/{{orderId}}");
        spec.setHeaders(List.of(authorization));
        spec.setCredentialRef("order-api");
        spec.setTimeoutMs(5000);
        spec.setResultPointer(pointer);

        ToolDefinitionBO definition = new ToolDefinitionBO();
        definition.setToolType(ToolType.HTTP);
        definition.setExposedName("query_order");
        definition.setDescription("查询订单");
        definition.setInputSchema(Map.of(
                "type", "object",
                "properties", Map.of(
                        "orderId", Map.of("type", "string"),
                        "verbose", Map.of("type", "boolean")),
                "required", List.of("orderId")));
        definition.setReadOnly(true);
        definition.setHttpSpec(spec);
        definition.setCredential("secret-value");
        return definition;
    }

    @Test
    void callsHttpToolExtractsResultAndMasksCredential() {
        ToolRunResultBO result = runner.run(httpTool("/data/status"), Map.of("orderId", "42", "verbose", true));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).isEqualTo("PAID");
        assertThat(result.getRequest()).contains("/orders/42").doesNotContain("secret-value");
        assertThat(lastAuthorization.get()).isEqualTo("Bearer secret-value");
        assertThat(lastQuery.get()).isEqualTo("verbose=true");
    }

    @Test
    void convertsHttpErrorsAndMasksCredentialInOutput() {
        ToolRunResultBO result = runner.run(httpTool(null), Map.of("orderId", "404"));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getOutput()).contains("HTTP 404").contains("******").doesNotContain("secret-value");
    }

    @Test
    void rejectsArgumentsThatViolateSchemaBeforeCalling() {
        ToolRunResultBO result = runner.run(httpTool(null), Map.of("verbose", true));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getOutput()).contains("orderId");
        assertThat(lastQuery.get()).isNull();
    }

    @Test
    void runsBuiltinToolUnderConfiguredName() {
        ToolDefinitionBO definition = new ToolDefinitionBO();
        definition.setToolType(ToolType.BUILTIN);
        definition.setImplementationKey("current_time");
        definition.setExposedName("now_in_zone");
        definition.setDescription("查询当前时间");

        ToolRunResultBO ok = runner.run(definition, Map.of("timezone", "Asia/Tokyo"));
        assertThat(ok.isSuccess()).isTrue();
        assertThat(ok.getOutput()).contains("Asia/Tokyo");

        ToolRunResultBO invalid = runner.run(definition, Map.of("timezone", "Mars/Base"));
        assertThat(invalid.isSuccess()).isFalse();
    }

    @Test
    void validatesSchemaShape() {
        assertThat(ToolSchemaRules.validate(Map.of("type", "object", "properties", Map.of("q", Map.of("type", "string")))))
                .containsExactly("q");
        assertThatThrownBy(() -> ToolSchemaRules.validate(Map.of("type", "array")))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> ToolSchemaRules.validate(Map.of("type", "object",
                "properties", Map.of("1bad", Map.of("type", "string")))))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> ToolSchemaRules.validate(Map.of("type", "object",
                "properties", Map.of(), "required", List.of("missing"))))
                .isInstanceOf(BusinessException.class);
    }
}
