package com.agentsquad.squad.v2.agent.tracing;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.middleware.ActingInput;
import io.agentscope.core.middleware.AgentInput;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.middleware.ModelCallInput;
import io.opentelemetry.api.trace.Span;
import reactor.core.publisher.Flux;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Squad V2 业务身份追踪
 * <p>
 * 官方 OTel 创建 Span 后统一补充原生用户、会话及运行编号；不推测未建立的租户 ID。
 */
public class FrameworkTracingMiddleware implements MiddlewareBase {
    private static final String TRACE_NAME_KEY = "agentsquad.tracing.root.name";
    private static final String SESSION_ID_KEY = "agentsquad.tracing.root.sessionId";

    private final LangfuseTraceSerializer serializer;

    public FrameworkTracingMiddleware() {
        this(false, 4096);
    }

    public FrameworkTracingMiddleware(boolean captureContent, int maxContentChars) {
        this(captureContent, maxContentChars, Math.min(maxContentChars, 2048));
    }

    public FrameworkTracingMiddleware(boolean captureContent, int maxContentChars, int maxToolContentChars) {
        this.serializer = new LangfuseTraceSerializer(captureContent, maxContentChars, maxToolContentChars);
    }

    @Override
    public Set<ExtensionPoint> activePoints() {
        return EnumSet.of(ExtensionPoint.ON_AGENT, ExtensionPoint.ON_MODEL_CALL, ExtensionPoint.ON_ACTING);
    }

    @Override
    public Flux<AgentEvent> onAgent(Agent agent, RuntimeContext context, AgentInput input,
                                   Function<AgentInput, Flux<AgentEvent>> next) {
        return Flux.deferContextual(view -> {
            Span span = TracingSpanContext.span(view);
            boolean appRoot = context.get(TRACE_NAME_KEY) == null;
            initializeTraceIdentity(agent, context);
            writeIdentity(span, context);
            if (span.getSpanContext().isValid()) {
                span.setAttribute("langfuse.observation.type", "agent");
                span.setAttribute("langfuse.internal.is_app_root", appRoot);
                writeContent(span, "langfuse.observation.input", serializer.messages(input.msgs()));
            }
            return next.apply(input).doOnNext(event -> {
                if (event instanceof AgentResultEvent result && result.getResult() != null) {
                    String output = serializer.messages(List.of(result.getResult()));
                    writeContent(span, "langfuse.observation.output", output);
                }
            });
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
     * 首个 Agent 确定展示归属，SDK 派生子上下文时继承；子会话另存为节点元数据。
     */
    private void initializeTraceIdentity(Agent agent, RuntimeContext context) {
        if (context.get(TRACE_NAME_KEY) == null && agent.getName() != null) {
            context.put(TRACE_NAME_KEY, agent.getName());
        }
        if (context.get(SESSION_ID_KEY) == null && context.getSessionId() != null) {
            context.put(SESSION_ID_KEY, context.getSessionId());
        }
    }

    private void writeContent(Span span, String key, String value) {
        if (span.getSpanContext().isValid() && value != null) {
            span.setAttribute(key, value);
        }
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
            String rootSessionId = context.get(SESSION_ID_KEY);
            span.setAttribute("langfuse.session.id", rootSessionId == null ? context.getSessionId() : rootSessionId);
            span.setAttribute("langfuse.observation.metadata.native_session_id", context.getSessionId());
        }
        String traceName = context.get(TRACE_NAME_KEY);
        if (traceName != null) {
            span.setAttribute("langfuse.trace.name", traceName);
        }
        span.setAttribute("agentsquad.run.id", context.getRunId());
    }
}
