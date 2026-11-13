package com.agentsquad.squad.v2.agent.observer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.*;
import io.agentscope.core.middleware.ActingInput;
import io.agentscope.core.middleware.AgentInput;
import io.agentscope.core.middleware.ModelCallInput;
import io.agentscope.core.session.SessionRecorder;
import io.agentscope.core.tracing.OtelTracingMiddleware;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.reactor.v3_1.ContextPropagationOperator;
import reactor.core.publisher.Flux;
import reactor.util.context.ContextView;

import java.util.function.Function;

/**
 * 原生 Agent 执行观测扩展
 * <p>
 * 在后台 AgentSession 真正执行时注入关联属性，Agent、模型和工具的 Span 仍由父类生成。
 */
public class AgentTracingMiddleware extends OtelTracingMiddleware {
    private final String environment;
    private final Tracer tracer;
    private static final int MAX_CONTENT_LENGTH = 16_000;

    public AgentTracingMiddleware(OpenTelemetry sdk, String environment) {
        super(sdk);
        this.environment = environment;
        this.tracer = sdk.getTracer("io.agentscope");
    }

    /**
     * 为本次原生执行绑定会话信息；流的完成与取消语义全部沿用框架。
     */
    @Override
    public Flux<AgentEvent> onAgent(Agent agent, RuntimeContext context, AgentInput input,
                                    Function<AgentInput, Flux<AgentEvent>> next) {
        return Flux.deferContextual(view -> {
            Context parent = ContextPropagationOperator.getOpenTelemetryContextFromContextView(view, Context.current());
            var baggage = Baggage.fromContext(parent).toBuilder();
            String userId = context.getUserId() == null ? "unknown" : context.getUserId();
            baggage.put("user.id", userId);
            baggage.put("gen_ai.conversation.id", userId + ":" + context.getSessionId());
            baggage.put("agentsquad.session.id", String.valueOf(context.getSessionId()));
            baggage.put("agentsquad.run.id", String.valueOf(context.getRunId()));
            baggage.put("agentsquad.turn.id", String.valueOf((Object) context.get(SessionRecorder.TURN_ID_KEY)));
            baggage.put("deployment.environment.name", environment);
            baggage.put("langfuse.observation.type", "agent");
            baggage.put("langfuse.observation.input", serialize(input.msgs()));
            Context linked = baggage.build().storeInContext(parent);
            return super.onAgent(agent, context, input, next)
                    .contextWrite(reactor -> ContextPropagationOperator.storeOpenTelemetryContext(reactor, linked));
        });
    }

    @Override
    public Flux<AgentEvent> onModelCall(Agent agent, RuntimeContext context, ModelCallInput input,
                                        Function<ModelCallInput, Flux<AgentEvent>> next) {
        return Flux.deferContextual(view -> {
            var parent = ContextPropagationOperator.getOpenTelemetryContextFromContextView(view, Context.current());
            String modelName = input.model() == null ? "unknown" : input.model().getModelName();
            var baggage = Baggage.fromContext(parent).toBuilder()
                    .put("langfuse.observation.type", "generation")
                    .put("langfuse.observation.input", serialize(input.messages()))
                    .put("agentscope.function.input", serialize(input.messages()));
            Context linked = baggage.build().storeInContext(parent);
            Span modelSpan = tracer.spanBuilder("chat " + modelName).setParent(linked)
                    .setAttribute("gen_ai.operation.name", "chat")
                    .setAttribute("gen_ai.request.model", modelName)
                    .setAttribute("gen_ai.request.messages.count", (long) input.messages().size())
                    .setAttribute("gen_ai.request.tools.count", (long) input.tools().size())
                    .startSpan();
            Context modelContext = modelSpan.storeInContext(linked);
            StringBuilder response = new StringBuilder();
            return next.apply(input)
                    .doOnEach(signal -> {
                        if (signal.hasValue()) {
                            AgentEvent event = signal.get();
                            if (event instanceof TextBlockDeltaEvent delta) {
                                response.append(delta.getDelta());
                            } else if (event instanceof ThinkingBlockDeltaEvent delta) {
                                modelSpan.setAttribute("gen_ai.response.reasoning", limit(delta.getDelta()));
                            } else if (event instanceof ModelCallEndEvent end && end.getUsage() != null) {
                                modelSpan.setAttribute("gen_ai.usage.input_tokens", (long) end.getUsage().getInputTokens());
                                modelSpan.setAttribute("gen_ai.usage.output_tokens", (long) end.getUsage().getOutputTokens());
                            }
                        } else if (signal.isOnComplete()) {
                            String output = serialize(response.toString());
                            modelSpan.setAttribute("langfuse.observation.output", output);
                            modelSpan.setAttribute("agentscope.function.output", output);
                            modelSpan.setStatus(StatusCode.OK);
                            modelSpan.end();
                        }
                    })
                    .doOnError(error -> {
                        modelSpan.recordException(error);
                        modelSpan.setStatus(StatusCode.ERROR);
                        modelSpan.end();
                    })
                    .contextWrite(reactor -> ContextPropagationOperator.storeOpenTelemetryContext(reactor, modelContext));
        });
    }

    @Override
    public Flux<AgentEvent> onActing(Agent agent, RuntimeContext context, ActingInput input,
                                     Function<ActingInput, Flux<AgentEvent>> next) {
        return Flux.deferContextual(view -> {
            var parent = ContextPropagationOperator.getOpenTelemetryContextFromContextView(view, Context.current());
            Context linked = Baggage.fromContext(parent).toBuilder()
                    .put("langfuse.observation.type", "tool")
                    .put("langfuse.observation.input", serialize(input.toolCalls()))
                    .put("agentscope.function.input", serialize(input.toolCalls()))
                    .build().storeInContext(parent);
            Span toolSpan = tracer.spanBuilder("execute_tool " + input.toolCalls().get(0).getName())
                    .setParent(linked)
                    .setAttribute("gen_ai.operation.name", "execute_tool")
                    .setAttribute("gen_ai.tool.name", input.toolCalls().get(0).getName())
                    .setAttribute("gen_ai.tool.call.count", (long) input.toolCalls().size())
                    .startSpan();
            Context toolContext = toolSpan.storeInContext(linked);
            StringBuilder result = new StringBuilder();
            return next.apply(input)
                    .doOnEach(signal -> {
                        if (signal.hasValue()) {
                            AgentEvent event = signal.get();
                            if (event instanceof ToolResultTextDeltaEvent delta) {
                                result.append(delta.getDelta());
                            } else if (event instanceof ToolResultEndEvent end) {
                                toolSpan.setAttribute("gen_ai.tool.result.status", end.getState().getValue());
                            }
                        } else if (signal.isOnComplete()) {
                            String output = serialize(result.toString());
                            toolSpan.setAttribute("langfuse.observation.output", output);
                            toolSpan.setAttribute("agentscope.function.output", output);
                            toolSpan.setStatus(StatusCode.OK);
                            toolSpan.end();
                        }
                    })
                    .doOnError(error -> {
                        toolSpan.recordException(error);
                        toolSpan.setStatus(StatusCode.ERROR);
                        toolSpan.end();
                    })
                    .contextWrite(reactor -> ContextPropagationOperator.storeOpenTelemetryContext(reactor, toolContext));
        });
    }

    private String limit(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= MAX_CONTENT_LENGTH ? value : value.substring(0, MAX_CONTENT_LENGTH) + "…";
    }

    /**
     * 将观测内容编码为标准 JSON；序列化失败时保留可读的降级值。
     */
    private String serialize(Object value) {
        try {
            return limit(new ObjectMapper().writeValueAsString(value));
        } catch (JsonProcessingException exception) {
            return limit(String.valueOf(value));
        }
    }

    /**
     * 从 Reactor 链路上下文取回 AgentScope 原生 Span，避免线程切换后写入错误 Span。
     */
    private Span span(Context context) {
        return Span.fromContext(context);
    }

    private Context signalContext(ContextView view) {
        return ContextPropagationOperator.getOpenTelemetryContextFromContextView(view, Context.current());
    }
}
