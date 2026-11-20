package com.agentsquad.squad.v2.agent.tracing;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AllToolsDeniedEvent;
import io.agentscope.core.event.RequireUserConfirmEvent;
import io.agentscope.core.event.ToolResultEndEvent;
import io.agentscope.core.event.ToolResultStartEvent;
import io.agentscope.core.event.ToolResultTextDeltaEvent;
import io.agentscope.core.middleware.ActingInput;
import io.agentscope.core.middleware.MiddlewareBase;
import io.opentelemetry.api.trace.Span;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * 工具调用追踪
 * <p>
 * 复用官方 execute_tool 批次 Span；按调用 ID 记录名称、审批与执行状态及结果大小。
 * 工具参数与原始执行结果不进入追踪系统。
 */
public class ToolCallTracingMiddleware implements MiddlewareBase {
    private static final int MAX_TOOLS = 20;

    private final LangfuseTraceSerializer serializer;

    public ToolCallTracingMiddleware(boolean captureContent, int maxContentChars) {
        this.serializer = new LangfuseTraceSerializer(captureContent, maxContentChars);
    }

    @Override
    public Set<ExtensionPoint> activePoints() {
        return EnumSet.of(ExtensionPoint.ON_ACTING);
    }

    @Override
    public Flux<AgentEvent> onActing(Agent agent, RuntimeContext context, ActingInput input,
                                    Function<ActingInput, Flux<AgentEvent>> next) {
        return Flux.deferContextual(view -> {
            Span span = TracingSpanContext.span(view);
            if (!span.getSpanContext().isValid()) {
                return next.apply(input);
            }

            span.setAttribute("langfuse.observation.type", "span");
            String arguments = serializer.toolCalls(input.toolCalls());
            span.setAttribute("gen_ai.tool.call.arguments", arguments);
            span.setAttribute("langfuse.observation.input", arguments);

            Map<String, Map<String, Object>> results = new LinkedHashMap<>();
            if (input.toolCalls() != null) {
                input.toolCalls().stream().limit(MAX_TOOLS).forEach(call -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", call.getId());
                    item.put("name", call.getName());
                    item.put("status", "pending");
                    results.put(call.getId(), item);
                });
            }

            Runnable finish = () -> {
                List<Map<String, Object>> summary = new ArrayList<>(results.values());
                String output = serializer.json(summary);
                if (output != null) {
                    span.setAttribute("gen_ai.tool.call.result", output);
                    span.setAttribute("langfuse.observation.output", output);
                }
            };
            finish.run();

            return next.apply(input)
                    .doOnNext(event -> recordEvent(event, results, finish))
                    .doOnComplete(finish)
                    .doOnError(error -> finish.run())
                    .doOnCancel(() -> finishQuietly(results, finish));
        });
    }

    /**
     * 变更时立即写入工具状态；异常或中断时仍保留最后已知状态。
     */
    private void recordEvent(AgentEvent event, Map<String, Map<String, Object>> results, Runnable finish) {
        if (event instanceof ToolResultStartEvent start) {
            setStatus(results, start.getToolCallId(), "running");
        } else if (event instanceof ToolResultEndEvent end) {
            String status = end.getState() == null ? "unknown"
                    : end.getState().name().toLowerCase(Locale.ROOT);
            setStatus(results, end.getToolCallId(), status);
        } else if (event instanceof RequireUserConfirmEvent) {
            setOpenStatus(results, "awaiting_confirmation");
        } else if (event instanceof AllToolsDeniedEvent) {
            setOpenStatus(results, "denied");
        } else if (event instanceof ToolResultTextDeltaEvent delta) {
            Map<String, Object> result = results.get(delta.getToolCallId());
            if (result != null && delta.getDelta() != null) {
                long count = (long) result.getOrDefault("output_chars", 0L);
                result.put("output_chars", count + delta.getDelta().length());
            }
            return;
        } else {
            return;
        }
        finish.run();
    }

    private void setStatus(Map<String, Map<String, Object>> results, String callId, String status) {
        Map<String, Object> entry = results.get(callId);
        if (entry != null) {
            entry.put("status", status);
        }
    }

    private void setOpenStatus(Map<String, Map<String, Object>> results, String status) {
        for (Map<String, Object> entry : results.values()) {
            if ("pending".equals(entry.get("status"))) {
                entry.put("status", status);
            }
        }
    }

    private void finishQuietly(Map<String, Map<String, Object>> results, Runnable finish) {
        try {
            setOpenStatus(results, "cancelled");
            finish.run();
        } catch (RuntimeException ignored) {
            // 取消线程与流事件可能并行，禁止观测异常覆盖业务取消结果。
        }
    }
}
