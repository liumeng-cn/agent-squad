package com.agentsquad.squad.v2;

import com.agentsquad.squad.v2.agent.observer.AgentTracingMiddleware;
import com.agentsquad.squad.v2.agent.tool.EventTestTool;
import com.agentsquad.squad.v2.config.properties.ObservationProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.ChatUsage;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.harness.agent.HarnessAgent;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.Flux;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 本机 Studio 端到端验收
 * <p>
 * 显式启用后使用可控模型向真实本地实例导出，并通过官方 API 回读；不调用外部模型。
 */
@SpringBootTest(properties = "agentsquad.v2.agent.enabled=false")
@EnabledIfEnvironmentVariable(named = "SQUAD_STUDIO_LOCAL_PROBE", matches = "true")
class StudioLocalProbeTest {
    @Resource
    private AgentTracingMiddleware middleware;

    @Resource
    private OpenTelemetrySdk observationSdk;

    @Resource
    private ObservationProperties properties;

    @TempDir
    Path workspace;

    @Test
    void exportsTwoTurnsWithToolAndReadsThemFromStudio() throws Exception {
        Model model = mock(Model.class);
        when(model.getModelName()).thenReturn("studio-local-probe");
        AtomicBoolean first = new AtomicBoolean(true);
        when(model.stream(any(), any(), any())).thenAnswer(invocation -> {
            if (first.getAndSet(false)) {
                return Flux.just(ChatResponse.builder().content(List.of(ToolUseBlock.builder()
                                .id("local-tool-call").name("test_event_tool").input(Map.of()).build()))
                        .usage(ChatUsage.builder().inputTokens(10).outputTokens(5).build()).finishReason("tool_calls").build());
            }
            return Flux.just(ChatResponse.builder().content(List.of(TextBlock.builder()
                    .text("本地观测验证完成").build())).usage(ChatUsage.builder().inputTokens(10).outputTokens(5).build()).finishReason("stop").build());
        });
        String sessionId = "local-probe-" + UUID.randomUUID();
        var toolkit = new Toolkit();
        toolkit.registerTool(new EventTestTool());
        try (var agent = HarnessAgent.builder().agentId("local-probe").name("本地接入验收")
                .model(model).toolkit(toolkit).middleware(middleware).workspace(workspace).build()) {
            var session = agent.session(RuntimeContext.builder().userId("local-probe").sessionId(sessionId).build());
            for (int i = 0; i < 2; i++) {
                var task = session.submit("probe-" + i, "本地合成验证输入");
                assertThat(session.await(task).block(Duration.ofSeconds(20)).status()).isEqualTo("completed");
            }
        }
        assertThat(observationSdk.getSdkTracerProvider().forceFlush().join(10, TimeUnit.SECONDS).isSuccess()).isTrue();
        // Studio 标准 OTLP 导出后，通过官方 tRPC 查询接口核对落库调用树。
        String base = properties.getEndpoint().replace("/v1/traces", "");
        try (var client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()) {
            for (int attempt = 0; attempt < 20; attempt++) {
                String input = java.net.URLEncoder.encode("{\"pagination\":{\"page\":1,\"pageSize\":100}}", StandardCharsets.UTF_8);
                var request = HttpRequest.newBuilder(URI.create(base + "/trpc/getTraces?input=" + input))
                        .timeout(Duration.ofSeconds(5)).GET().build();
                var response = client.send(request, HttpResponse.BodyHandlers.ofString());
                assertThat(response.statusCode()).isEqualTo(200);
                var mapper = new ObjectMapper();
                var list = mapper.readTree(response.body()).path("result").path("data").path("data").path("list");
                int matched = 0;
                var operations = new java.util.HashSet<String>();
                for (var item : list) {
                    String detailInput = java.net.URLEncoder.encode(
                            "{\"traceId\":\"" + item.path("traceId").asText() + "\"}", StandardCharsets.UTF_8);
                    var detailRequest = HttpRequest.newBuilder(URI.create(base + "/trpc/getTrace?input=" + detailInput))
                            .timeout(Duration.ofSeconds(5)).GET().build();
                    var detail = client.send(detailRequest, HttpResponse.BodyHandlers.ofString());
                    assertThat(detail.statusCode()).isEqualTo(200);
                    var trace = mapper.readTree(detail.body()).path("result").path("data");
                    if (!trace.toString().contains(sessionId)) {
                        continue;
                    }
                    matched++;
                    for (var span : trace.path("spans")) {
                        assertThat(span.path("conversationId").asText()).isEqualTo("local-probe:" + sessionId);
                        operations.add(span.path("attributes").path("gen_ai").path("operation").path("name").asText());
                    }
                    assertThat(trace.path("totalTokens").asInt()).isPositive();
                }
                if (matched == 2) {
                    assertThat(operations).contains("invoke_agent", "chat", "execute_tool");
                    return;
                }
                Thread.sleep(1000);
            }
        }
        throw new AssertionError("Studio 未返回本轮观测数据");
    }
}
