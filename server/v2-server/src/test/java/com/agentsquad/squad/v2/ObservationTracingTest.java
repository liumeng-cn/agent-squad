package com.agentsquad.squad.v2;

import com.agentsquad.squad.v2.agent.middleware.LangfuseEnrichmentMiddleware;
import com.agentsquad.squad.v2.agent.tracing.FrameworkTracingMiddleware;
import com.agentsquad.squad.v2.agent.tracing.HttpTracingFilter;
import com.agentsquad.squad.v2.agent.tracing.HttpTracingBodyAdvice;
import com.agentsquad.squad.v2.agent.tracing.LangfuseTraceSerializer;
import com.agentsquad.squad.v2.agent.tracing.ModelIoTracingMiddleware;
import com.agentsquad.squad.v2.agent.tracing.ReasoningTracingMiddleware;
import com.agentsquad.squad.v2.agent.tracing.ToolCallTracingMiddleware;
import com.agentsquad.squad.v2.agent.tool.EventTestTool;
import com.agentsquad.squad.v2.config.ObservationConfiguration;
import com.agentsquad.squad.v2.config.properties.ObservationProperties;
import com.agentsquad.squad.v2.controller.ChatController;
import com.agentsquad.squad.v2.service.ChatEventStreamService;
import com.agentsquad.squad.v2.service.ChatService;
import com.agentsquad.squad.v2.vo.ChatReceiptVO;
import com.agentsquad.squad.v2.vo.ChatSessionVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.ChatUsage;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.harness.agent.HarnessAgent;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
                        .text("测试完成").build()))
                        .usage(ChatUsage.builder().inputTokens(12).outputTokens(6).build())
                        .finishReason("stop").build());
            });
            var toolkit = new Toolkit();
            toolkit.registerTool(new EventTestTool());
            try (var agent = HarnessAgent.builder().agentId("trace-test").name("trace-test")
                    .model(model).toolkit(toolkit)
                    .middleware(new io.agentscope.core.tracing.OtelTracingMiddleware(sdk))
                    .middleware(new ModelIoTracingMiddleware(true, 4096))
                    .middleware(new ToolCallTracingMiddleware(true, 4096))
                    .middleware(new FrameworkTracingMiddleware(true, 4096))
                    .middleware(new ReasoningTracingMiddleware(sdk.getTracer("trace-test"), true, 4096))
                    .workspace(workspace).build()) {
                var session = agent.session(RuntimeContext.builder().userId("user-1").sessionId("session-1").build());
                var task = session.submit("request-1", "调用测试工具");
                assertThat(session.await(task).block(Duration.ofSeconds(20)).status()).isEqualTo("completed");
            }
            var spans = exporter.getFinishedSpanItems();
            assertThat(spans).hasSizeGreaterThanOrEqualTo(6);
            assertThat(spans).allSatisfy(span -> {
                assertThat(span.getAttributes().get(AttributeKey.stringKey("langfuse.trace.name")))
                        .isEqualTo("trace-test");
                assertThat(span.getAttributes().get(AttributeKey.stringKey("langfuse.user.id"))).isEqualTo("user-1");
                assertThat(span.getAttributes().get(AttributeKey.stringKey("langfuse.session.id")))
                        .isEqualTo("session-1");
            });
            assertThat(spans.stream().map(span -> span.getTraceId()).distinct()).hasSize(1);
            assertThat(spans.stream().filter(span -> Boolean.TRUE.equals(span.getAttributes()
                    .get(AttributeKey.booleanKey("langfuse.internal.is_app_root"))))).hasSize(1);
            assertThat(spans.stream().filter(span -> span.getName().startsWith("invoke_agent")))
                    .allSatisfy(span -> {
                        assertThat(span.getAttributes().get(AttributeKey.stringKey("langfuse.observation.input")))
                                .isNotBlank();
                        assertThat(span.getAttributes().get(AttributeKey.stringKey("langfuse.observation.output")))
                                .isNotBlank();
                    });
            assertThat(spans).anyMatch(span -> "execute_tool".equals(span.getAttributes()
                    .get(AttributeKey.stringKey("gen_ai.operation.name"))));
            assertThat(spans).anyMatch(span -> "chat".equals(span.getAttributes()
                    .get(AttributeKey.stringKey("gen_ai.operation.name"))));
            assertThat(spans).filteredOn(span -> "reasoning".equals(span.getAttributes()
                    .get(AttributeKey.stringKey("gen_ai.operation.name")))).hasSizeGreaterThanOrEqualTo(2)
                    .allSatisfy(span -> {
                        assertThat(span.getAttributes().get(AttributeKey.stringKey("langfuse.observation.input")))
                                .contains("调用测试工具");
                        assertThat(span.getAttributes().get(AttributeKey.stringKey("langfuse.observation.output")))
                                .isNotBlank();
                    });
            assertThat(spans).filteredOn(span -> "chat".equals(span.getAttributes()
                    .get(AttributeKey.stringKey("gen_ai.operation.name")))).allSatisfy(span ->
                    assertThat(spans).anyMatch(parent -> parent.getSpanId().equals(span.getParentSpanId())
                            && "reasoning".equals(parent.getName())));
            assertThat(spans).anyMatch(span -> span.getAttributes()
                    .get(AttributeKey.stringKey("langfuse.observation.input")) != null);
            assertThat(spans).anyMatch(span -> "generation".equals(span.getAttributes()
                    .get(AttributeKey.stringKey("langfuse.observation.type")))
                    && span.getAttributes().get(AttributeKey.stringKey("langfuse.observation.output")) != null);
            assertThat(spans).anyMatch(span -> "generation".equals(span.getAttributes()
                    .get(AttributeKey.stringKey("langfuse.observation.type")))
                    && span.getAttributes().get(AttributeKey.longKey("gen_ai.usage.input_tokens")) != null
                    && span.getAttributes().get(AttributeKey.stringKey("langfuse.observation.usage_details")) != null);
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
                                            empty -> Flux.just(new io.agentscope.core.event.AgentResultEvent(
                                                    io.agentscope.core.message.Msg.builder()
                                                            .role(io.agentscope.core.message.MsgRole.ASSISTANT)
                                                            .textContent("合成回复").build())))))
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
            assertThat(spans.stream().filter(span -> Boolean.TRUE.equals(span.getAttributes()
                    .get(AttributeKey.booleanKey("langfuse.internal.is_app_root"))))).hasSize(1);
            assertThat(spans.stream().filter(span -> span.getName().startsWith("invoke_agent")))
                    .allSatisfy(span -> {
                        assertThat(span.getAttributes().get(AttributeKey.stringKey("langfuse.observation.input")))
                                .isNotBlank();
                        assertThat(span.getAttributes().get(AttributeKey.stringKey("langfuse.observation.output")))
                                .isNotBlank();
                    });
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
            var modelIo = new ModelIoTracingMiddleware(false, 4096);
            var context = RuntimeContext.builder().sessionId("thinking-session").build();
            Flux<io.agentscope.core.event.AgentEvent> events = Flux.just(
                    new io.agentscope.core.event.ThinkingBlockDeltaEvent("reply", "thinking", "先分析"),
                    new io.agentscope.core.event.ThinkingBlockDeltaEvent("reply", "thinking", "再核对"));
            tracing.onModelCall(mock(io.agentscope.core.agent.Agent.class), context, input,
                    value -> modelIo.onModelCall(null, context, value,
                            ignored -> events.publishOn(reactor.core.scheduler.Schedulers.boundedElastic())))
                    .blockLast(Duration.ofSeconds(2));

            assertThat(exporter.getFinishedSpanItems()).singleElement().satisfies(span -> {
                assertThat(span.getAttributes().get(AttributeKey.longKey("agentsquad.reasoning.chars")))
                        .isEqualTo(6L);
                assertThat(span.getAttributes().get(AttributeKey.stringKey("langfuse.observation.metadata.thinking")))
                        .isNull();
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
            var modelIo = new ModelIoTracingMiddleware(false, 4096);
            var agent = mock(io.agentscope.core.agent.Agent.class);
            var model = mock(Model.class);
            when(model.getModelName()).thenReturn("lifecycle-test");
            var input = mock(io.agentscope.core.middleware.ModelCallInput.class);
            when(input.model()).thenReturn(model);
            when(input.messages()).thenReturn(List.of());
            when(input.tools()).thenReturn(List.of());
            var context = RuntimeContext.builder().userId("1").sessionId("lifecycle").build();

            tracing.onModelCall(agent, context, input,
                    value -> modelIo.onModelCall(agent, context, value, ignored -> Flux.never()))
                    .take(Duration.ofMillis(50)).blockLast(Duration.ofSeconds(2));
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> tracing.onModelCall(agent, context, input,
                    value -> modelIo.onModelCall(agent, context, value,
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
            properties.setPublicKey("pk-test");
            properties.setSecretKey("sk-test");
            io.opentelemetry.api.GlobalOpenTelemetry.resetForTest();
            var configuration = new ObservationConfiguration();
            ReflectionTestUtils.setField(configuration, "properties", properties);
            try (var sdk = configuration.observationSdk();
                 var agent = HarnessAgent.builder().agentId("export-failure").name("export-failure")
                         .model(new ScriptedChatModel()).workspace(workspace)
                         .middleware(new io.agentscope.core.tracing.OtelTracingMiddleware(sdk))
                         .middleware(new ModelIoTracingMiddleware(false, 4096))
                         .middleware(new ToolCallTracingMiddleware(false, 4096))
                         .middleware(new FrameworkTracingMiddleware())
                         .middleware(new ReasoningTracingMiddleware(sdk.getTracer("export-failure")))
                         .build()) {
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
            properties.setPublicKey("pk-test");
            properties.setSecretKey("sk-test");
            io.opentelemetry.api.GlobalOpenTelemetry.resetForTest();
            var configuration = new ObservationConfiguration();
            ReflectionTestUtils.setField(configuration, "properties", properties);
            try (var sdk = configuration.observationSdk()) {
                sdk.getTracer("test").spanBuilder("export-test").startSpan().end();
                assertThat(sdk.getSdkTracerProvider().forceFlush().join(5, TimeUnit.SECONDS).isSuccess()).isTrue();
            }
            assertThat(authorization.get()).isEqualTo("Basic " + Base64.getEncoder()
                    .encodeToString("pk-test:sk-test".getBytes(StandardCharsets.UTF_8)));
            assertThat(version.get()).isEqualTo("4");
            assertThat(bytes.get()).isPositive();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void observationMustHaveBothLangfuseKeysWhenEnabled() {
        var properties = new ObservationProperties();
        var configuration = new ObservationConfiguration();
        ReflectionTestUtils.setField(configuration, "properties", properties);
        assertThatThrownBy(configuration::observationSdk)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("public-key");
    }

    @Test
    void defaultModeDoesNotExportPromptOrReasoning() {
        var exporter = InMemorySpanExporter.create();
        var provider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter)).build();
        try (var sdk = OpenTelemetrySdk.builder().setTracerProvider(provider).build()) {
            var tracing = new io.agentscope.core.tracing.OtelTracingMiddleware(sdk);
            var modelIo = new ModelIoTracingMiddleware(false, 4096);
            var framework = new FrameworkTracingMiddleware();
            var model = mock(Model.class);
            when(model.getModelName()).thenReturn("privacy-test");
            var input = mock(io.agentscope.core.middleware.ModelCallInput.class);
            when(input.model()).thenReturn(model);
            when(input.messages()).thenReturn(List.of());
            when(input.tools()).thenReturn(List.of());
            var context = RuntimeContext.builder().userId("1").sessionId("privacy-session").build();

            tracing.onModelCall(mock(io.agentscope.core.agent.Agent.class), context, input,
                    value -> modelIo.onModelCall(null, context, value,
                            state -> framework.onModelCall(null, context, state, ignored -> Flux.just(
                                    new io.agentscope.core.event.ThinkingBlockDeltaEvent("reply", "thinking", "秘密推理"),
                                    new io.agentscope.core.event.TextBlockDeltaEvent("reply", "text", "敏感正文")))))
                    .blockLast(Duration.ofSeconds(2));

            assertThat(exporter.getFinishedSpanItems()).singleElement().satisfies(span -> {
                assertThat(span.getAttributes().get(AttributeKey.stringKey("langfuse.observation.input")))
                        .isNull();
                assertThat(span.getAttributes().get(AttributeKey.stringKey("langfuse.observation.output")))
                        .doesNotContain("敏感正文", "秘密推理");
                assertThat(span.getAttributes().get(AttributeKey.stringKey("langfuse.user.id")))
                        .isEqualTo("1");
            });
        }
    }

    @Test
    void contentOptInKeepsToolSecretsAndSystemPromptOutOfAttributes() {
        var serializer = new LangfuseTraceSerializer(true, 256);
        Msg system = Msg.builder().role(MsgRole.SYSTEM).textContent("系统密钥只能留在上下文").build();
        Msg user = Msg.builder().role(MsgRole.USER).textContent("用户提问").build();
        var call = ToolUseBlock.builder().id("call-1").name("test_event_tool")
                .input(Map.of("password", "工具参数中的秘密")).build();

        String messages = serializer.messages(List.of(system, user));
        String tools = serializer.toolCalls(List.of(call));
        assertThat(messages).contains("用户提问").doesNotContain("系统密钥只能留在上下文");
        assertThat(tools).contains("test_event_tool", "argument_count")
                .doesNotContain("工具参数中的秘密", "password");

        var options = GenerateOptions.builder().apiKey("super-secret-api-key")
                .temperature(0.7).build();
        assertThat(serializer.modelParameters(options)).contains("temperature")
                .doesNotContain("super-secret-api-key");

        String longText = "公开正文".repeat(500);
        assertThat(serializer.modelOutput(longText, List.of()).length()).isLessThanOrEqualTo(256);
    }

    @Test
    void keepsSystemAndMcpStructuresWithoutRepeatedContent() throws Exception {
        var serializer = new LangfuseTraceSerializer(true, 4096);
        var mapper = new ObjectMapper();
        Msg system = Msg.builder().role(MsgRole.SYSTEM).textContent("重复的系统正文".repeat(1000)).build();
        Msg user = Msg.builder().role(MsgRole.USER).textContent("请查询库存").build();
        String messages = serializer.messages(List.of(system, user));
        var parsed = mapper.readTree(messages);
        assertThat(parsed.size()).isEqualTo(2);
        assertThat(parsed.get(0).path("reference").asText()).startsWith("system:");
        assertThat(parsed.get(0).path("characters").asInt()).isEqualTo(7000);
        assertThat(messages).contains("请查询库存", "system prompt omitted").doesNotContain("重复的系统正文");
        assertThat(serializer.messages(List.of(system, user))).isEqualTo(messages);
        Msg changed = Msg.builder().role(MsgRole.SYSTEM).textContent("另一版本系统正文").build();
        assertThat(mapper.readTree(serializer.messages(List.of(changed, user))).get(0).path("reference"))
                .isNotEqualTo(parsed.get(0).path("reference"));

        var tool = ToolSchema.builder().name("mcp_inventory_search")
                .description("重复的工具说明".repeat(1000))
                .parameters(Map.of("type", "object", "properties", Map.of("vin", Map.of(
                        "type", "string", "description", "字段说明正文", "default", "不应输出的默认值")),
                        "required", List.of("vin")))
                .build();
        String definitions = serializer.toolDefinitions(List.of(tool));
        assertThat(definitions).contains("mcp_inventory_search", "tool:", "properties", "vin", "string", "required")
                .doesNotContain("重复的工具说明", "字段说明正文", "不应输出的默认值");
        var call = ToolUseBlock.builder().id("mcp-call").name(tool.getName())
                .input(Map.of("vin", "实际车辆编号", "apiKey", "绝不能输出的密钥")).build();
        assertThat(serializer.toolCalls(List.of(call))).contains("vin", "string", "实际车辆编号")
                .doesNotContain("绝不能输出的密钥", "apiKey");
    }

    @Test
    void toolPreviewsRedactSecretsAndRespectIndependentLimit() throws Exception {
        var serializer = new LangfuseTraceSerializer(true, 32768, 256);
        String result = serializer.toolResultPreview("password=synthetic-secret, Authorization: Bearer synthetic-token, "
                + "工具正常返回".repeat(1000));
        assertThat(result).contains("<redacted>").doesNotContain("synthetic-secret", "synthetic-token");
        assertThat(result.length()).isLessThanOrEqualTo(129);

        String output = serializer.toolResults(List.of(Map.of("name", "mcp_inventory_search", "status", "completed",
                "text", result, "output_chars", 6000)));
        assertThat(output.length()).isLessThanOrEqualTo(256);
        new ObjectMapper().readTree(output);
        var call = ToolUseBlock.builder().id("tool-call").name("mcp_inventory_search")
                .input(Map.of("query", "查询库存".repeat(1000), "nested", Map.of("password", "nested-secret"))).build();
        String input = serializer.toolCalls(List.of(call));
        assertThat(input.length()).isLessThanOrEqualTo(256);
        assertThat(input).doesNotContain("nested-secret");
        new ObjectMapper().readTree(input);
    }

    @Test
    void httpFilterSkipsRequestsOutsideChat() throws Exception {
        var exporter = InMemorySpanExporter.create();
        var provider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter)).build();
        try (var sdk = OpenTelemetrySdk.builder().setTracerProvider(provider).build()) {
            var filter = new HttpTracingFilter();
            ReflectionTestUtils.setField(filter, "observationSdk", sdk);
            ReflectionTestUtils.setField(filter, "httpTraceSerializer", new LangfuseTraceSerializer(false, 4096));
            for (String path : List.of("/api/v2/agents", "/api/v2/auth/login", "/actuator/health", "/api/v2/chatty")) {
                filter.doFilter(new MockHttpServletRequest("GET", path), new MockHttpServletResponse(), new MockFilterChain());
            }
            assertThat(exporter.getFinishedSpanItems()).isEmpty();
            filter.doFilter(new MockHttpServletRequest("GET", "/api/v2/chat/convs/one/session"),
                    new MockHttpServletResponse(), new MockFilterChain());
            assertThat(exporter.getFinishedSpanItems()).isEmpty();
            filter.doFilter(new MockHttpServletRequest("POST", "/api/v2/chat/messages"),
                    new MockHttpServletResponse(), new MockFilterChain());
            assertThat(exporter.getFinishedSpanItems()).hasSize(1);
        }
    }

    @Test
    void oversizedContentKeepsReadablePreviewWithinJsonLimit() throws Exception {
        var serializer = new LangfuseTraceSerializer(true, 256);
        String encoded = serializer.json(Map.of("text", "可读内容\"\\😀".repeat(1000)));
        assertThat(encoded.length()).isLessThanOrEqualTo(256);
        var value = new ObjectMapper().readTree(encoded);
        assertThat(value.path("truncated").asBoolean()).isTrue();
        assertThat(value.path("characters").asInt()).isGreaterThan(256);
        assertThat(value.path("preview").asText()).contains("可读内容");
    }

    @Test
    void httpJsonBodiesAreCapturedWithoutChangingTheWireResponse() throws Exception {
        var exporter = InMemorySpanExporter.create();
        var provider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter)).build();
        try (var sdk = OpenTelemetrySdk.builder().setTracerProvider(provider).build()) {
            var mvc = httpMvc(sdk, new LangfuseTraceSerializer(true, 4096, 4096));
            mvc.perform(post("/api/v2/chat/messages").contentType(MediaType.APPLICATION_JSON)
                            .param("token", "query-secret").header("Cookie", "session=private-cookie")
                            .content("{\"convId\":\"http-test\",\"interactionId\":\"request-1\",\"message\":\"验证 password=body-secret\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.inputId").value("trace-command"));

            assertThat(exporter.getFinishedSpanItems()).singleElement().satisfies(span -> {
                String input = span.getAttributes().get(AttributeKey.stringKey("langfuse.observation.input"));
                String output = span.getAttributes().get(AttributeKey.stringKey("langfuse.observation.output"));
                assertThat(input).contains("http-test", "request-1", "<redacted>")
                        .doesNotContain("query-secret", "body-secret", "private-cookie");
                assertThat(output).contains("trace-command", "code", "status");
            });
        }
    }

    @Test
    void httpValidationErrorBodyIsCaptured() throws Exception {
        var exporter = InMemorySpanExporter.create();
        var provider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter)).build();
        try (var sdk = OpenTelemetrySdk.builder().setTracerProvider(provider).build()) {
            httpMvc(sdk, new LangfuseTraceSerializer(true, 4096, 4096))
                    .perform(post("/api/v2/chat/messages").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"convId\":\"\",\"interactionId\":\"request-1\",\"message\":\"验证\"}"))
                    .andExpect(jsonPath("$.code").value(400));
            assertThat(exporter.getFinishedSpanItems()).singleElement().satisfies(span -> {
                String output = span.getAttributes().get(AttributeKey.stringKey("langfuse.observation.output"));
                assertThat(output).contains("\"code\":400", "\"msg\"");
            });
        }
    }

    @Test
    void httpContentCanBeDisabledAndOversizedBodiesKeepPreview() throws Exception {
        var limited = new LangfuseTraceSerializer(true, 256, 256);
        String preview = limited.httpPayload(Map.of("first", "正文".repeat(500), "second", "正文".repeat(500),
                "third", "正文".repeat(500), "thinking", "内部推理不能采集"));
        assertThat(preview.length()).isLessThanOrEqualTo(256);
        assertThat(preview).contains("truncated", "preview", "正文").doesNotContain("内部推理不能采集");
        new ObjectMapper().readTree(preview);

        var exporter = InMemorySpanExporter.create();
        var provider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter)).build();
        try (var sdk = OpenTelemetrySdk.builder().setTracerProvider(provider).build()) {
            httpMvc(sdk, new LangfuseTraceSerializer(false, 4096))
                    .perform(post("/api/v2/chat/messages").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"convId\":\"http-test\",\"interactionId\":\"request-1\",\"message\":\"验证\"}"))
                    .andExpect(status().isOk());
            assertThat(exporter.getFinishedSpanItems()).singleElement().satisfies(span -> {
                assertThat(span.getAttributes().get(AttributeKey.stringKey("langfuse.observation.input"))).isNull();
                assertThat(span.getAttributes().get(AttributeKey.stringKey("langfuse.observation.output"))).isNull();
            });
        }
    }

    @Test
    void httpCaptureDoesNotBufferSseEvents() throws Exception {
        var exporter = InMemorySpanExporter.create();
        var provider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter)).build();
        try (var sdk = OpenTelemetrySdk.builder().setTracerProvider(provider).build()) {
            var mvc = httpMvc(sdk, new LangfuseTraceSerializer(true, 4096, 4096));
            var response = mvc.perform(get("/api/v2/chat/convs/http-test/events").accept(MediaType.TEXT_EVENT_STREAM))
                    .andExpect(request().asyncStarted()).andReturn();
            response.getAsyncResult(2000);
            var completed = mvc.perform(asyncDispatch(response)).andExpect(status().isOk()).andReturn();
            assertThat(completed.getResponse().getContentAsString()).contains("event:session", "data:");
            assertThat(exporter.getFinishedSpanItems()).isEmpty();
        }
    }

    private MockMvc httpMvc(OpenTelemetrySdk sdk, LangfuseTraceSerializer serializer) {
        var controller = new ChatController();
        var service = mock(ChatService.class);
        var receipt = new ChatReceiptVO();
        receipt.setConvId("http-test");
        receipt.setInputId("trace-command");
        when(service.send(any())).thenReturn(receipt);
        ReflectionTestUtils.setField(controller, "chatService", service);

        var events = mock(ChatEventStreamService.class);
        when(events.subscribe(any())).thenReturn(Flux.just(ServerSentEvent.<ChatSessionVO>builder()
                .event("session").data(new ChatSessionVO()).build()));
        ReflectionTestUtils.setField(controller, "eventService", events);
        var advice = new HttpTracingBodyAdvice();
        ReflectionTestUtils.setField(advice, "httpTraceSerializer", serializer);
        var filter = new HttpTracingFilter();
        ReflectionTestUtils.setField(filter, "observationSdk", sdk);
        ReflectionTestUtils.setField(filter, "httpTraceSerializer", serializer);
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(advice, new com.agentsquad.squad.common.web.GlobalExceptionHandler())
                .addFilters(filter).build();
    }

    @Test
    void httpFilterAcceptsW3cParentWithoutExtendingIntoAgentExecution() throws Exception {
        var exporter = InMemorySpanExporter.create();
        var provider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter)).build();
        try (var sdk = OpenTelemetrySdk.builder().setTracerProvider(provider).build()) {
            var filter = new HttpTracingFilter();
            ReflectionTestUtils.setField(filter, "observationSdk", sdk);
            ReflectionTestUtils.setField(filter, "httpTraceSerializer", new LangfuseTraceSerializer(false, 4096));
            var request = new MockHttpServletRequest("POST", "/api/v2/chat/messages");
            request.addHeader("traceparent",
                    "00-0123456789abcdef0123456789abcdef-0123456789abcdef-01");
            var response = new MockHttpServletResponse();
            filter.doFilter(request, response, new MockFilterChain());

            assertThat(exporter.getFinishedSpanItems()).singleElement().satisfies(span -> {
                assertThat(span.getTraceId()).isEqualTo("0123456789abcdef0123456789abcdef");
                assertThat(span.getParentSpanId()).isEqualTo("0123456789abcdef");
                assertThat(span.getAttributes().get(AttributeKey.longKey("http.response.status_code")))
                        .isEqualTo(200L);
            });
        }
    }
}
