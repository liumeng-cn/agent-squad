package com.agentsquad.squad.v2.agent.tracing;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.middleware.ActingInput;
import io.agentscope.core.middleware.AgentInput;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.middleware.ModelCallInput;
import io.opentelemetry.api.trace.Span;
import reactor.core.publisher.Flux;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.Function;

/**
 * Squad V2 业务身份追踪
 * <p>
 * 官方 OTel 创建 Span 后统一补充原生用户、会话及运行编号；不推测未建立的租户 ID。
 */
public class FrameworkTracingMiddleware implements MiddlewareBase {

    @Override
    public Set<ExtensionPoint> activePoints() {
        return EnumSet.of(ExtensionPoint.ON_AGENT, ExtensionPoint.ON_MODEL_CALL, ExtensionPoint.ON_ACTING);
    }

    @Override
    public Flux<AgentEvent> onAgent(Agent agent, RuntimeContext context, AgentInput input,
                                   Function<AgentInput, Flux<AgentEvent>> next) {
        return Flux.deferContextual(view -> {
            Span span = TracingSpanContext.span(view);
            writeIdentity(span, context);
            if (span.getSpanContext().isValid()) {
                span.setAttribute("langfuse.trace.name", agent.getName());
                span.setAttribute("langfuse.observation.type", "agent");
            }
            return next.apply(input);
        });
    }

    @Override
    public Flux<AgentEvent> onModelCall(Agent agent, RuntimeContext context, ModelCallInput input,
                                       Function<ModelCallInput, Flux<AgentEvent>> next) {
        return Flux.deferContextual(view -> {
            writeIdentity(TracingSpanContext.span(view), context);
            return next.apply(input);
        });
    }

    @Override
    public Flux<AgentEvent> onActing(Agent agent, RuntimeContext context, ActingInput input,
                                    Function<ActingInput, Flux<AgentEvent>> next) {
        return Flux.deferContextual(view -> {
            writeIdentity(TracingSpanContext.span(view), context);
            return next.apply(input);
        });
    }

    /**
     * 一次 hook 写入一次，与每个 AgentEvent 的数量无关。
     */
    static void writeIdentity(Span span, RuntimeContext context) {
        if (!span.getSpanContext().isValid() || context == null) {
            return;
        }
        if (context.getUserId() != null) {
            span.setAttribute("langfuse.user.id", context.getUserId());
        }
        if (context.getSessionId() != null) {
            span.setAttribute("langfuse.session.id", context.getSessionId());
        }
        span.setAttribute("agentsquad.run.id", context.getRunId());
    }
}
