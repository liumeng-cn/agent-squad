package com.agentsquad.squad.v2.agent.middleware;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ThinkingBlockDeltaEvent;
import io.agentscope.core.event.ToolResultTextDeltaEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.middleware.ActingInput;
import io.agentscope.core.middleware.AgentInput;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.middleware.ModelCallInput;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.reactor.v3_1.ContextPropagationOperator;
import reactor.core.publisher.Flux;
import reactor.util.context.ContextView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * Langfuse 属性增强
 *
 * 仅补充官方追踪中间件已创建的 Span，不创建或结束 Span。
 * 采集模型公开返回的文本、思考增量及工具调用；不创建额外的追踪 Span。
 */
public class LangfuseEnrichmentMiddleware implements MiddlewareBase {
    private static final int MAX_CONTENT_LENGTH = 16000;
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public Flux<AgentEvent> onAgent(Agent agent, RuntimeContext context, AgentInput input,
                                   Function<AgentInput, Flux<AgentEvent>> next) {
        return Flux.deferContextual(view -> {
            Context parent = currentContext(view);
            Context linked = associationContext(parent, context);
            Span span = Span.fromContext(parent);
            enrichAssociation(span, linked);
            attribute(span, "langfuse.observation.metadata.native_session_id", context.getSessionId());
            attribute(span, "langfuse.trace.name", agent.getName());
            attribute(span, "langfuse.trace.input", messages(input.msgs()));
            return next.apply(input).doOnNext(event -> {
                if (event instanceof AgentResultEvent result) {
                    attribute(span, "langfuse.trace.output", messages(List.of(result.getResult())));
                }
            }).contextWrite(reactor -> ContextPropagationOperator.storeOpenTelemetryContext(reactor, linked));
        });
    }

    @Override
    public Flux<AgentEvent> onModelCall(Agent agent, RuntimeContext context, ModelCallInput input,
                                       Function<ModelCallInput, Flux<AgentEvent>> next) {
        return Flux.deferContextual(view -> {
            Span span = currentSpan(view);
            enrichAssociation(span, currentContext(view));
            attribute(span, "langfuse.observation.type", "generation");
            attribute(span, "langfuse.observation.model.name", input.model().getModelName());
            attribute(span, "langfuse.observation.input", messages(input.messages()));
            attribute(span, "langfuse.observation.tools", json(input.tools()));
            StringBuilder output = new StringBuilder();
            StringBuilder thinking = new StringBuilder();
            AtomicBoolean thinkingActive = new AtomicBoolean();
            return next.apply(input)
                    .doOnNext(event -> recordModelEvent(event, span, thinking, output, thinkingActive))
                    .doOnComplete(() -> {
                        finishThinking(span, thinkingActive);
                        attribute(span, "langfuse.observation.output", json(output.toString()));
                    })
                    .doOnError(error -> finishThinking(span, thinkingActive))
                    .doOnCancel(() -> finishThinking(span, thinkingActive));
        });
    }

    @Override
    public Flux<AgentEvent> onActing(Agent agent, RuntimeContext context, ActingInput input,
                                    Function<ActingInput, Flux<AgentEvent>> next) {
        return Flux.deferContextual(view -> {
            Span span = currentSpan(view);
            enrichAssociation(span, currentContext(view));
            attribute(span, "langfuse.observation.type", "span");
            attribute(span, "langfuse.observation.input", json(input.toolCalls()));
            StringBuilder output = new StringBuilder();
            return next.apply(input).doOnNext(event -> {
                if (event instanceof ToolResultTextDeltaEvent delta) {
                    append(output, delta.getDelta());
                }
            }).doOnComplete(() -> attribute(span, "langfuse.observation.output", json(output.toString())));
        });
    }

    /**
     * 累积模型公开返回的思考增量，正文开始时结束思考阶段。
     */
    private void recordModelEvent(AgentEvent event, Span span, StringBuilder thinking,
                                  StringBuilder output, AtomicBoolean thinkingActive) {
        if (event instanceof ThinkingBlockDeltaEvent delta) {
            if (delta.getDelta() == null || delta.getDelta().isEmpty()) {
                return;
            }
            if (thinkingActive.compareAndSet(false, true)) {
                span.addEvent("thinking.started");
            }
            append(thinking, delta.getDelta());
            // 增量回填，异常或取消时也保留模型已返回的思考内容。
            attribute(span, "langfuse.observation.metadata.thinking", thinking.toString());
        } else if (event instanceof TextBlockDeltaEvent delta) {
            finishThinking(span, thinkingActive);
            append(output, delta.getDelta());
        }
    }

    private void finishThinking(Span span, AtomicBoolean active) {
        if (active.compareAndSet(true, false)) {
            span.addEvent("thinking.ended");
        }
    }

    /**
     * 首次调用确定根会话，子 Agent 继承既有归属而不覆盖为自身的原生会话。
     */
    private Context associationContext(Context parent, RuntimeContext context) {
        Baggage inherited = Baggage.fromContext(parent);
        String sessionId = inherited.getEntryValue("langfuse.session.id");
        if (sessionId == null) {
            sessionId = context.getSessionId();
        }
        String userId = inherited.getEntryValue("langfuse.user.id");
        if (userId == null) {
            userId = context.getUserId();
        }
        var baggage = inherited.toBuilder();
        if (sessionId != null) {
            baggage.put("langfuse.session.id", sessionId);
        }
        if (userId != null) {
            baggage.put("langfuse.user.id", userId);
        }
        return baggage.build().storeInContext(parent);
    }

    /**
     * 整条调用链沿用主 Agent 的会话归属，子 Agent 原生会话单独保留为元数据。
     */
    private void enrichAssociation(Span span, Context context) {
        Baggage baggage = Baggage.fromContext(context);
        attribute(span, "langfuse.session.id", baggage.getEntryValue("langfuse.session.id"));
        attribute(span, "langfuse.user.id", baggage.getEntryValue("langfuse.user.id"));
    }

    private Context currentContext(ContextView view) {
        return ContextPropagationOperator.getOpenTelemetryContextFromContextView(view, Context.current());
    }

    private Span currentSpan(ContextView view) {
        return Span.fromContext(currentContext(view));
    }

    /**
     * 显式选择公开消息字段，不序列化原始消息中的推理块和扩展上下文。
     */
    private String messages(List<Msg> messages) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Msg message : messages) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("role", message.getRole());
            item.put("content", message.getContentBlocks(TextBlock.class));
            item.put("tool_calls", message.getContentBlocks(ToolUseBlock.class));
            List<Map<String, Object>> toolResults = new ArrayList<>();
            for (ToolResultBlock block : message.getContentBlocks(ToolResultBlock.class)) {
                Map<String, Object> toolResult = new LinkedHashMap<>();
                toolResult.put("name", block.getName());
                toolResult.put("output", block.getOutput());
                toolResults.add(toolResult);
            }
            item.put("tool_results", toolResults);
            result.add(item);
        }
        return json(result);
    }

    private void append(StringBuilder output, String delta) {
        if (delta != null && output.length() < MAX_CONTENT_LENGTH) {
            int remaining = MAX_CONTENT_LENGTH - output.length();
            output.append(delta, 0, Math.min(delta.length(), remaining));
        }
    }

    private void attribute(Span span, String name, String value) {
        if (value != null) {
            span.setAttribute(name, value);
        }
    }

    private String json(Object value) {
        try {
            String encoded = mapper.writeValueAsString(value);
            if (encoded.length() > MAX_CONTENT_LENGTH) {
                return mapper.writeValueAsString(encoded.substring(0, MAX_CONTENT_LENGTH));
            }
            return encoded;
        } catch (JsonProcessingException exception) {
            return null;
        }
    }
}
