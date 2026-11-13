package com.agentsquad.squad.v2.agent.observer;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SpanProcessor;

import java.util.Set;

/**
 * 观测平台 Span 属性补充
 * <p>
 * 将执行上下文中的用户、会话与原生标识传播到每个子 Span，不复制模型和工具埋点。
 */
public class TraceContextSpanProcessor implements SpanProcessor {
    private static final Set<String> CONTEXT_KEYS = Set.of(
            "user.id", "gen_ai.conversation.id", "agentsquad.session.id",
            "agentsquad.run.id", "agentsquad.turn.id", "deployment.environment.name",
            "langfuse.observation.type", "langfuse.observation.input",
            "agentscope.function.input");

    @Override
    public void onStart(Context parentContext, ReadWriteSpan span) {
        Baggage.fromContext(parentContext).forEach((key, entry) -> {
            if (CONTEXT_KEYS.contains(key)) {
                span.setAttribute(key, entry.getValue());
            }
        });

    }

    @Override
    public boolean isStartRequired() {
        return true;
    }

    @Override
    public void onEnd(ReadableSpan span) {
        // 批量导出由标准 BatchSpanProcessor 负责。
    }

    @Override
    public boolean isEndRequired() {
        return false;
    }
}
