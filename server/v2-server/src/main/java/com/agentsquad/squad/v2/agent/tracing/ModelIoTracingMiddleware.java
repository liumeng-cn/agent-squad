package com.agentsquad.squad.v2.agent.tracing;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.ModelCallEndEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ThinkingBlockDeltaEvent;
import io.agentscope.core.event.ToolCallDeltaEvent;
import io.agentscope.core.event.ToolCallEndEvent;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.middleware.ModelCallInput;
import io.opentelemetry.api.trace.Span;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * 模型真实 I/O 追踪
 * <p>
 * 补充官方 chat Span 的模型输入、流式输出、首字时间及用量。
 * 正文经脱敏序列化器控制，思考只记录长度，不记录推理文本。
 */
public class ModelIoTracingMiddleware implements MiddlewareBase {
    private static final int MAX_TOOL_CALLS = 20;

    private final LangfuseTraceSerializer serializer;
    private final int maxContentChars;

    public ModelIoTracingMiddleware(boolean captureContent, int maxContentChars) {
        this(captureContent, maxContentChars, Math.min(maxContentChars, 2048));
    }

    public ModelIoTracingMiddleware(boolean captureContent, int maxContentChars, int maxToolContentChars) {
        this.serializer = new LangfuseTraceSerializer(captureContent, maxContentChars, maxToolContentChars);
        this.maxContentChars = maxContentChars;
    }

    @Override
    public Set<ExtensionPoint> activePoints() {
        return EnumSet.of(ExtensionPoint.ON_MODEL_CALL);
    }

    @Override
    public Flux<AgentEvent> onModelCall(Agent agent, RuntimeContext context, ModelCallInput input,
                                       Function<ModelCallInput, Flux<AgentEvent>> next) {
        return Flux.deferContextual(view -> {
            Span span = TracingSpanContext.span(view);
            if (!span.getSpanContext().isValid()) {
                return next.apply(input);
            }

            span.setAttribute("langfuse.observation.type", "generation");
            String modelName = input.model() == null ? "unknown" : input.model().getModelName();
            span.setAttribute("langfuse.observation.model.name", modelName);
            write(span, "langfuse.observation.model.parameters", serializer.modelParameters(input.options()));
            String messages = serializer.messages(input.messages());
            write(span, "gen_ai.input.messages", messages);
            write(span, "langfuse.observation.input", serializer.modelInput(input.messages(), input.tools()));
            write(span, "langfuse.observation.metadata.tools", serializer.toolDefinitions(input.tools()));
            if (input.tools() != null) {
                String toolNames = String.join(",", input.tools().stream().limit(MAX_TOOL_CALLS)
                        .map(tool -> tool.getName()).toList());
                span.setAttribute("agentsquad.tools.visible", serializer.limit(toolNames));
            }

            LangfuseModelTraceState state = new LangfuseModelTraceState();
            Runnable finish = () -> {
                if (state.thinkingActive.compareAndSet(true, false)) {
                    span.addEvent("thinking.ended");
                }
                String result = serializer.modelOutput(state.text, state.toolCalls);
                write(span, "gen_ai.output.messages", result);
                write(span, "langfuse.observation.output", result);
            };

            return next.apply(input)
                    .doOnNext(event -> recordEvent(event, span, state))
                    .doOnComplete(finish)
                    .doOnError(error -> finish.run())
                    .doOnCancel(() -> finishQuietly(finish));
        });
    }

    /**
     * 模型事件按实际到达顺序记录；流式工具参数仅计数，不保存参数正文。
     */
    private void recordEvent(AgentEvent event, Span span, LangfuseModelTraceState state) {
        if (event instanceof ThinkingBlockDeltaEvent delta) {
            markFirstToken(span, state);
            if (state.thinkingActive.compareAndSet(false, true)) {
                span.addEvent("thinking.started");
            }
            if (delta.getDelta() != null) {
                span.setAttribute("agentsquad.reasoning.chars",
                        state.reasoningChars.addAndGet(delta.getDelta().length()));
            }
        } else if (event instanceof TextBlockDeltaEvent delta) {
            markFirstToken(span, state);
            if (state.thinkingActive.compareAndSet(true, false)) {
                span.addEvent("thinking.ended");
            }
            if (delta.getDelta() != null && state.text.length() < maxContentChars) {
                int count = Math.min(delta.getDelta().length(), maxContentChars - state.text.length());
                state.text.append(delta.getDelta(), 0, count);
            }
        } else if (event instanceof ToolCallDeltaEvent delta) {
            String callId = delta.getToolCallId();
            if (delta.getDelta() != null &&
                    (state.argumentSizes.size() < MAX_TOOL_CALLS || state.argumentSizes.containsKey(callId))) {
                state.argumentSizes.merge(callId, delta.getDelta().length(), Integer::sum);
            }
        } else if (event instanceof ToolCallEndEvent end) {
            if (state.toolCalls.size() < MAX_TOOL_CALLS) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", end.getToolCallId());
                item.put("name", end.getToolCallName());
                item.put("arguments_chars", state.argumentSizes.getOrDefault(end.getToolCallId(), 0));
                state.toolCalls.add(item);
            }
        } else if (event instanceof ModelCallEndEvent end) {
            write(span, "langfuse.observation.usage_details", serializer.usageDetails(end.getUsage()));
        }
    }

    private void markFirstToken(Span span, LangfuseModelTraceState state) {
        if (state.firstDelta.compareAndSet(false, true)) {
            span.setAttribute("langfuse.observation.completion_start_time", Instant.now().toString());
        }
    }

    private void write(Span span, String key, String value) {
        if (value != null) {
            span.setAttribute(key, value);
        }
    }

    private void finishQuietly(Runnable finish) {
        try {
            finish.run();
        } catch (RuntimeException ignored) {
            // 取消信号可能与上游事件并发，观测不能改变取消语义。
        }
    }
}
