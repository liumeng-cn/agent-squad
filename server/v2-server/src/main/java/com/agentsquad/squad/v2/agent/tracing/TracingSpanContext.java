package com.agentsquad.squad.v2.agent.tracing;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.reactor.v3_1.ContextPropagationOperator;
import reactor.util.context.ContextView;

/**
 * AgentScope Reactor 链路中的 OTel 上下文读取
 * <p>
 * 避免跨线程时误用 ThreadLocal 中的 Span.current()。
 */
final class TracingSpanContext {
    private TracingSpanContext() {
    }

    static Context context(ContextView view) {
        return ContextPropagationOperator.getOpenTelemetryContextFromContextView(view, Context.current());
    }

    static Span span(ContextView view) {
        return Span.fromContext(context(view));
    }
}
