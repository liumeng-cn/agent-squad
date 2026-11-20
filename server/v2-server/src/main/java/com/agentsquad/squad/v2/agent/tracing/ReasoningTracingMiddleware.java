package com.agentsquad.squad.v2.agent.tracing;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
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

    public ReasoningTracingMiddleware(Tracer tracer) {
        this.tracer = tracer;
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
            AtomicBoolean ended = new AtomicBoolean();

            try {
                return ContextPropagationOperator.runWithContext(
                        next.apply(input)
                                .doOnComplete(() -> end(span, ended, StatusCode.OK, null))
                                .doOnError(error -> end(span, ended, StatusCode.ERROR, error))
                                .doOnCancel(() -> end(span, ended, StatusCode.UNSET, null)),
                        span.storeInContext(parent));
            } catch (RuntimeException error) {
                end(span, ended, StatusCode.ERROR, error);
                throw error;
            }
        });
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
