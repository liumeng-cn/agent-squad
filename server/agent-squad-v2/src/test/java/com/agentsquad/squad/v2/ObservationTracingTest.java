package com.agentsquad.squad.v2;

import com.agentsquad.squad.v2.agent.observer.TraceContextSpanProcessor;
import com.agentsquad.squad.v2.agent.observer.AgentTracingMiddleware;
import com.agentsquad.squad.v2.agent.tool.EventTestTool;
import com.agentsquad.squad.v2.config.ObservationConfiguration;
import com.agentsquad.squad.v2.config.properties.ObservationProperties;
import com.sun.net.httpserver.HttpServer;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.harness.agent.HarnessAgent;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Studio 原生观测验收
 * <p>
 * 在真实后台 Session 中检查异步父子关系、每个 Span 的会话归属以及 OTLP 鉴权导出。
 */
class ObservationTracingTest {
    @TempDir
    Path workspace;

    @Test
    void keepsAgentModelAndToolInOneTraceWithSessionOnEverySpan() {
        var exporter = InMemorySpanExporter.create();
        var provider = SdkTracerProvider.builder().addSpanProcessor(new TraceContextSpanProcessor())
                .addSpanProcessor(SimpleSpanProcessor.create(exporter)).build();
        try (var sdk = OpenTelemetrySdk.builder().setTracerProvider(provider).build()) {
            var middleware = new AgentTracingMiddleware(sdk, "test");
            Model model = mock(Model.class);
            when(model.getModelName()).thenReturn("trace-test");
            AtomicBoolean first = new AtomicBoolean(true);
            when(model.stream(any(), any(), any())).thenAnswer(invocation -> {
                if (first.getAndSet(false)) {
                    return Flux.just(ChatResponse.builder().content(List.of(ToolUseBlock.builder()
                                    .id("trace-call").name("test_event_tool").input(Map.of()).build()))
                            .finishReason("tool_calls").build());
                }
                return Flux.just(ChatResponse.builder().content(List.of(TextBlock.builder()
                        .text("测试完成").build())).finishReason("stop").build());
            });
            var toolkit = new Toolkit();
            toolkit.registerTool(new EventTestTool());
            try (var agent = HarnessAgent.builder().agentId("trace-test").name("trace-test")
                    .model(model).toolkit(toolkit).middleware(middleware).workspace(workspace).build()) {
                var session = agent.session(RuntimeContext.builder().userId("user-1").sessionId("session-1").build());
                var task = session.submit("request-1", "调用测试工具");
                assertThat(session.await(task).block(Duration.ofSeconds(20)).status()).isEqualTo("completed");
            }
            var spans = exporter.getFinishedSpanItems();
            assertThat(spans).hasSizeGreaterThanOrEqualTo(4);
            assertThat(spans).allSatisfy(span -> {
                assertThat(span.getAttributes().get(AttributeKey.stringKey("user.id"))).isEqualTo("user-1");
                assertThat(span.getAttributes().get(AttributeKey.stringKey("gen_ai.conversation.id")))
                        .isEqualTo("user-1:session-1");
            });
            assertThat(spans.stream().map(span -> span.getTraceId()).distinct()).hasSize(1);
            assertThat(spans).anyMatch(span -> "execute_tool".equals(span.getAttributes()
                    .get(AttributeKey.stringKey("gen_ai.operation.name"))));
            assertThat(spans).anyMatch(span -> "chat".equals(span.getAttributes()
                    .get(AttributeKey.stringKey("gen_ai.operation.name"))));
            assertThat(spans).anyMatch(span -> span.getAttributes()
                    .get(AttributeKey.stringKey("langfuse.observation.input")) != null);
            assertThat(spans).anyMatch(span -> "generation".equals(span.getAttributes()
                    .get(AttributeKey.stringKey("langfuse.observation.type")))
                    && span.getAttributes().get(AttributeKey.stringKey("langfuse.observation.output")) != null);
            assertThat(spans).anyMatch(span -> "tool".equals(span.getAttributes()
                    .get(AttributeKey.stringKey("langfuse.observation.type")))
                    && span.getAttributes().get(AttributeKey.stringKey("langfuse.observation.output")) != null);
        }
    }

    @Test
    void exportFailureDoesNotChangeNativeExecutionResult() throws Exception {
        AtomicBoolean rejected = new AtomicBoolean();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/traces", exchange -> {
            exchange.getRequestBody().readAllBytes();
            rejected.set(true);
            exchange.sendResponseHeaders(401, -1);
            exchange.close();
        });
        server.start();
        try {
            var properties = new ObservationProperties();
            properties.setEndpoint("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/traces");
            var configuration = new ObservationConfiguration();
            ReflectionTestUtils.setField(configuration, "properties", properties);
            try (var sdk = configuration.observationSdk();
                 var agent = HarnessAgent.builder().agentId("export-failure").name("export-failure")
                         .model(new ScriptedChatModel()).workspace(workspace)
                         .middleware(new AgentTracingMiddleware(sdk, "test")).build()) {
                var session = agent.session(RuntimeContext.builder().userId("1").sessionId("failure").build());
                var task = session.submit("request", "测试输入");
                assertThat(session.await(task).block(Duration.ofSeconds(20)).status()).isEqualTo("completed");
                sdk.getSdkTracerProvider().forceFlush().join(5, TimeUnit.SECONDS);
                assertThat(rejected.get()).isTrue();
                assertThat(session.task(task.commandId()).status()).isEqualTo("completed");
            }
        } finally {
            server.stop(0);
        }
    }

    @Test
    void exportsProtobufToOtlpHttpWithoutBackendSpecificHeaders() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> version = new AtomicReference<>();
        AtomicReference<Integer> bytes = new AtomicReference<>();
        server.createContext("/v1/traces", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            version.set(exchange.getRequestHeaders().getFirst("x-langfuse-ingestion-version"));
            bytes.set(exchange.getRequestBody().readAllBytes().length);
            exchange.getResponseHeaders().add("Content-Type", "application/x-protobuf");
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        try {
            var properties = new ObservationProperties();
            properties.setEndpoint("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/traces");
            var configuration = new ObservationConfiguration();
            ReflectionTestUtils.setField(configuration, "properties", properties);
            try (var sdk = configuration.observationSdk()) {
                sdk.getTracer("test").spanBuilder("export-test").startSpan().end();
                assertThat(sdk.getSdkTracerProvider().forceFlush().join(5, TimeUnit.SECONDS).isSuccess()).isTrue();
            }
            assertThat(authorization.get()).isNull();
            assertThat(version.get()).isNull();
            assertThat(bytes.get()).isPositive();
        } finally {
            server.stop(0);
        }
    }
}
