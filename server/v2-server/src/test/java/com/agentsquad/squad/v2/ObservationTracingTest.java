package com.agentsquad.squad.v2;

import com.agentsquad.squad.v2.agent.middleware.LangfuseEnrichmentMiddleware;
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
 * Langfuse 追踪验收
 * <p>
 * 在真实后台 Session 中检查异步父子关系、根 Span 的会话归属以及 OTLP 鉴权导出。
 */
class ObservationTracingTest {
    @TempDir
    Path workspace;

    @Test
    void keepsAgentModelAndToolInOneTraceWithSessionOnEverySpan() {
        var exporter = InMemorySpanExporter.create();
        var provider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter)).build();
        try (var sdk = OpenTelemetrySdk.builder().setTracerProvider(provider).build()) {
            var middleware = new LangfuseEnrichmentMiddleware();
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
                    .model(model).toolkit(toolkit).middleware(new io.agentscope.core.tracing.OtelTracingMiddleware(sdk)).middleware(middleware).workspace(workspace).build()) {
                var session = agent.session(RuntimeContext.builder().userId("user-1").sessionId("session-1").build());
                var task = session.submit("request-1", "调用测试工具");
                assertThat(session.await(task).block(Duration.ofSeconds(20)).status()).isEqualTo("completed");
            }
            var spans = exporter.getFinishedSpanItems();
            assertThat(spans).hasSizeGreaterThanOrEqualTo(4);
            assertThat(spans).allSatisfy(span -> {
                assertThat(span.getAttributes().get(AttributeKey.stringKey("langfuse.user.id"))).isEqualTo("user-1");
                assertThat(span.getAttributes().get(AttributeKey.stringKey("langfuse.session.id")))
                        .isEqualTo("session-1");
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
            assertThat(spans).anyMatch(span -> "span".equals(span.getAttributes()
                    .get(AttributeKey.stringKey("langfuse.observation.type")))
                    && span.getAttributes().get(AttributeKey.stringKey("langfuse.observation.output")) != null);
        }
    }

    @Test
    void childAgentKeepsRootSessionAssociationAcrossAsyncExecution() {
        var exporter = InMemorySpanExporter.create();
        var provider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter)).build();
        try (var sdk = OpenTelemetrySdk.builder().setTracerProvider(provider).build()) {
            var tracing = new io.agentscope.core.tracing.OtelTracingMiddleware(sdk);
            var enrichment = new LangfuseEnrichmentMiddleware();
            var parent = mock(io.agentscope.core.agent.Agent.class);
            var child = mock(io.agentscope.core.agent.Agent.class);
            when(parent.getName()).thenReturn("parent");
            when(child.getName()).thenReturn("child");
            var input = mock(io.agentscope.core.middleware.AgentInput.class);
            when(input.msgs()).thenReturn(List.of());
            var parentContext = RuntimeContext.builder().userId("owner").sessionId("parent-session").build();
            var childContext = RuntimeContext.builder().userId("owner").sessionId("child-session").build();

            tracing.onAgent(parent, parentContext, input,
                    parentInput -> enrichment.onAgent(parent, parentContext, parentInput,
                            ignored -> Flux.defer(() -> tracing.onAgent(child, childContext, input,
                                    childInput -> enrichment.onAgent(child, childContext, childInput,
                                            empty -> Flux.empty())))
                                    .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())))
                    .blockLast(Duration.ofSeconds(2));

            var spans = exporter.getFinishedSpanItems();
            assertThat(spans).hasSize(2).allSatisfy(span -> {
                assertThat(span.getAttributes().get(AttributeKey.stringKey("langfuse.session.id")))
                        .isEqualTo("parent-session");
                assertThat(span.getAttributes().get(AttributeKey.stringKey("langfuse.user.id")))
                        .isEqualTo("owner");
            });
            assertThat(spans.stream().map(span -> span.getTraceId()).distinct()).hasSize(1);
            assertThat(spans).filteredOn(span -> span.getName().equals("invoke_agent child"))
                    .singleElement().satisfies(span -> assertThat(span.getAttributes()
                            .get(AttributeKey.stringKey("langfuse.observation.metadata.native_session_id")))
                            .isEqualTo("child-session"));
        }
    }

    @Test
    void enrichesThinkingReturnedByModelAcrossAsyncThread() {
        var exporter = InMemorySpanExporter.create();
        var provider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter)).build();
        try (var sdk = OpenTelemetrySdk.builder().setTracerProvider(provider).build()) {
            var model = mock(Model.class);
            when(model.getModelName()).thenReturn("thinking-test");
            var input = mock(io.agentscope.core.middleware.ModelCallInput.class);
            when(input.model()).thenReturn(model);
            when(input.messages()).thenReturn(List.of());
            when(input.tools()).thenReturn(List.of());
            var tracing = new io.agentscope.core.tracing.OtelTracingMiddleware(sdk);
            var enrichment = new LangfuseEnrichmentMiddleware();
            var context = RuntimeContext.builder().sessionId("thinking-session").build();
            Flux<io.agentscope.core.event.AgentEvent> events = Flux.just(
                    new io.agentscope.core.event.ThinkingBlockDeltaEvent("reply", "thinking", "先分析"),
                    new io.agentscope.core.event.ThinkingBlockDeltaEvent("reply", "thinking", "再核对"));
            tracing.onModelCall(mock(io.agentscope.core.agent.Agent.class), context, input,
                    value -> enrichment.onModelCall(null, context, value,
                            ignored -> events.publishOn(reactor.core.scheduler.Schedulers.boundedElastic())))
                    .blockLast(Duration.ofSeconds(2));

            assertThat(exporter.getFinishedSpanItems()).singleElement().satisfies(span -> {
                assertThat(span.getAttributes().get(AttributeKey.stringKey("langfuse.observation.metadata.thinking")))
                        .isEqualTo("先分析再核对");
                assertThat(span.getEvents()).extracting(event -> event.getName())
                        .containsExactly("thinking.started", "thinking.ended");
            });
        }
    }

    @Test
    void officialMiddlewareEndsModelSpanOnFailureAndCancellation() {
        var exporter = InMemorySpanExporter.create();
        var provider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter)).build();
        try (var sdk = OpenTelemetrySdk.builder().setTracerProvider(provider).build()) {
            var tracing = new io.agentscope.core.tracing.OtelTracingMiddleware(sdk);
            var enrichment = new LangfuseEnrichmentMiddleware();
            var agent = mock(io.agentscope.core.agent.Agent.class);
            var model = mock(Model.class);
            when(model.getModelName()).thenReturn("lifecycle-test");
            var input = mock(io.agentscope.core.middleware.ModelCallInput.class);
            when(input.model()).thenReturn(model);
            when(input.messages()).thenReturn(List.of());
            when(input.tools()).thenReturn(List.of());
            var context = RuntimeContext.builder().userId("1").sessionId("lifecycle").build();

            tracing.onModelCall(agent, context, input,
                    value -> enrichment.onModelCall(agent, context, value, ignored -> Flux.never()))
                    .take(Duration.ofMillis(50)).blockLast(Duration.ofSeconds(2));
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> tracing.onModelCall(agent, context, input,
                    value -> enrichment.onModelCall(agent, context, value,
                            ignored -> Flux.error(new IllegalStateException("合成模型故障"))))
                    .blockLast(Duration.ofSeconds(2))).isInstanceOf(IllegalStateException.class);

            assertThat(exporter.getFinishedSpanItems()).hasSize(2).allSatisfy(span -> {
                assertThat(span.getStatus().getStatusCode()).isEqualTo(io.opentelemetry.api.trace.StatusCode.ERROR);
                assertThat(span.getAttributes().get(AttributeKey.stringKey("langfuse.observation.type")))
                        .isEqualTo("generation");
                assertThat(span.getAttributes().get(AttributeKey.stringKey("gen_ai.response.reasoning"))).isNull();
            });
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
            io.opentelemetry.api.GlobalOpenTelemetry.resetForTest();
            var configuration = new ObservationConfiguration();
            ReflectionTestUtils.setField(configuration, "properties", properties);
            try (var sdk = configuration.observationSdk();
                 var agent = HarnessAgent.builder().agentId("export-failure").name("export-failure")
                         .model(new ScriptedChatModel()).workspace(workspace)
                         .middleware(new io.agentscope.core.tracing.OtelTracingMiddleware(sdk))
                         .middleware(new LangfuseEnrichmentMiddleware()).build()) {
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
    void exportsProtobufWithLangfuseIngestionHeader() throws Exception {
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
            io.opentelemetry.api.GlobalOpenTelemetry.resetForTest();
            var configuration = new ObservationConfiguration();
            ReflectionTestUtils.setField(configuration, "properties", properties);
            try (var sdk = configuration.observationSdk()) {
                sdk.getTracer("test").spanBuilder("export-test").startSpan().end();
                assertThat(sdk.getSdkTracerProvider().forceFlush().join(5, TimeUnit.SECONDS).isSuccess()).isTrue();
            }
            assertThat(authorization.get()).isNull();
            assertThat(version.get()).isEqualTo("4");
            assertThat(bytes.get()).isPositive();
        } finally {
            server.stop(0);
        }
    }
}
