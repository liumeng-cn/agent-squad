package com.agentsquad.squad.v2.agent.tracing;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ThinkingBlockDeltaEvent;
import io.agentscope.core.event.ToolCallEndEvent;
import io.agentscope.core.middleware.AgentInput;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.middleware.ReasoningInput;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.reactor.v3_1.ContextPropagationOperator;
import reactor.core.publisher.Flux;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * ReAct 每轮推理追踪
 * <p>
 * 官方 OtelTracingMiddleware 不创建 reasoning Span，这里在 invoke_agent 下创建推理轮次；
 * 模型 chat Span 会继承本轮上下文，工具批次仍与 reasoning 平级。
 */
public class ReasoningTracingMiddleware implements MiddlewareBase {
    private static final String COUNTER_KEY = "agentsquad.tracing.reasoning.round.";

    private final Tracer tracer;
    private final LangfuseTraceSerializer serializer;
    private final int maxContentChars;

    public ReasoningTracingMiddleware(Tracer tracer) {
        this(tracer, false, 4096);
    }

    public ReasoningTracingMiddleware(Tracer tracer, boolean captureContent, int maxContentChars) {
        this(tracer, captureContent, maxContentChars, Math.min(maxContentChars, 2048));
    }

    public ReasoningTracingMiddleware(Tracer tracer, boolean captureContent, int maxContentChars, int maxToolContentChars) {
        this.tracer = tracer;
        this.serializer = new LangfuseTraceSerializer(captureContent, maxContentChars, maxToolContentChars);
        this.maxContentChars = maxContentChars;
    }

    @Override
    public Set<ExtensionPoint> activePoints() {
        return EnumSet.of(ExtensionPoint.ON_AGENT, ExtensionPoint.ON_REASONING);
    }

    @Override
    public Flux<AgentEvent> onAgent(Agent agent, RuntimeContext context, AgentInput input,
                                   Function<AgentInput, Flux<AgentEvent>> next) {
        return Flux.defer(() -> {
            context.put(counterKey(agent), new AtomicInteger());
            return next.apply(input);
        });
    }

    @Override
    public Flux<AgentEvent> onReasoning(Agent agent, RuntimeContext context, ReasoningInput input,
                                       Function<ReasoningInput, Flux<AgentEvent>> next) {
        return Flux.deferContextual(view -> {
            Context parent = TracingSpanContext.context(view);
            if (!Span.fromContext(parent).getSpanContext().isValid()) {
                return next.apply(input);
            }

            AtomicInteger counter = context.get(counterKey(agent), AtomicInteger.class);
            int round = counter == null ? 1 : counter.incrementAndGet();
            Span span = tracer.spanBuilder("reasoning")
                    .setParent(parent)
                    .setAttribute("gen_ai.operation.name", "reasoning")
                    .setAttribute("agentsquad.reasoning.round", (long) round)
                    .setAttribute("gen_ai.request.messages.count",
                            input.messages() == null ? 0L : (long) input.messages().size())
                    .startSpan();
            FrameworkTracingMiddleware.writeIdentity(span, context);
            span.setAttribute("langfuse.observation.type", "chain");
            writeContent(span, "langfuse.observation.input", serializer.modelInput(input.messages(), input.tools()));
            writeContent(span, "langfuse.observation.metadata.tools", serializer.toolDefinitions(input.tools()));
            LangfuseModelTraceState output = new LangfuseModelTraceState();
            Runnable finishOutput = () -> {
                writeContent(span, "langfuse.observation.output", serializer.modelOutput(output.text, output.toolCalls));
                span.setAttribute("agentsquad.reasoning.chars", output.reasoningChars.get());
            };
            AtomicBoolean ended = new AtomicBoolean();

            try {
                return ContextPropagationOperator.runWithContext(
                        next.apply(input)
                                .doOnNext(event -> recordOutput(event, output))
                                .doOnComplete(() -> {
                                    finishOutput.run();
                                    end(span, ended, StatusCode.OK, null);
                                })
                                .doOnError(error -> {
                                    finishOutput.run();
                                    end(span, ended, StatusCode.ERROR, error);
                                })
                                .doOnCancel(() -> {
                                    finishOutput.run();
                                    end(span, ended, StatusCode.UNSET, null);
                                }),
                        span.storeInContext(parent));
            } catch (RuntimeException error) {
                end(span, ended, StatusCode.ERROR, error);
                throw error;
            }
        });
    }

    /**
     * 展示本轮公开回复和工具选择，思考增量只计量，不保存正文。
     */
    private void recordOutput(AgentEvent event, LangfuseModelTraceState output) {
        if (event instanceof TextBlockDeltaEvent delta && delta.getDelta() != null) {
            int remaining = maxContentChars - output.text.length();
            if (remaining > 0) {
                output.text.append(delta.getDelta(), 0, Math.min(remaining, delta.getDelta().length()));
            }
        } else if (event instanceof ToolCallEndEvent call && output.toolCalls.size() < 20) {
            Map<String, Object> tool = new LinkedHashMap<>();
            tool.put("id", call.getToolCallId());
            tool.put("name", call.getToolCallName());
            output.toolCalls.add(tool);
        } else if (event instanceof ThinkingBlockDeltaEvent delta && delta.getDelta() != null) {
            output.reasoningChars.addAndGet(delta.getDelta().length());
        }
    }

    private void writeContent(Span span, String key, String value) {
        if (value != null) {
            span.setAttribute(key, value);
        }
    }

    private String counterKey(Agent agent) {
        String name = agent.getAgentId() == null ? agent.getName() : agent.getAgentId();
        return COUNTER_KEY + name;
    }

    /**
     * 一次 span 只收尾一次；取消作为正常中断，不伪报模型故障。
     */
    private void end(Span span, AtomicBoolean ended, StatusCode status, Throwable error) {
        if (!ended.compareAndSet(false, true)) {
            return;
        }
        if (error != null) {
            span.recordException(error);
        }
        span.setStatus(status);
        span.end();
    }
}
