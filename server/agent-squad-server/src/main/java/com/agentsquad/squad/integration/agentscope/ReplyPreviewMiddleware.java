package com.agentsquad.squad.integration.agentscope;

import com.agentsquad.squad.conversation.repository.ResponseRuntimeMapper;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ThinkingBlockDeltaEvent;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.middleware.ModelCallInput;
import io.agentscope.core.model.GenerateOptions;
import jakarta.annotation.Resource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * 自然回复的公开正文流适配
 * <p>
 * 只为已持久受理的普通回复开启模型流，不开放Worker或目标协调回合；原始事件原样交还SDK，推送不控制模型订阅。
 * 推理增量只上报阶段心跳，不上送推理正文。
 */
@Component
@lombok.extern.slf4j.Slf4j
@ConditionalOnProperty(prefix = "agentsquad.delivery", name = "enabled", havingValue = "true")
public class ReplyPreviewMiddleware implements MiddlewareBase {
    @Resource
    private ResponseRuntimeMapper replies;

    @Resource
    private ApplicationEventPublisher previews;

    @Override
    public Set<ExtensionPoint> activePoints() {
        return EnumSet.of(ExtensionPoint.ON_MODEL_CALL);
    }

    @Override
    public Flux<AgentEvent> onModelCall(Agent agent, RuntimeContext context, ModelCallInput input,
                                        Function<ModelCallInput, Flux<AgentEvent>> next) {
        return Mono.fromCallable(() -> Optional.ofNullable(replies.previewTarget(context.getUserId(), context.getSessionId())))
                .subscribeOn(Schedulers.boundedElastic()).flatMapMany(target -> {
                    if (target.isEmpty()) {
                        return next.apply(input);
                    }
                    var buffer = new ReplyPreviewBuffer(target.get(), context.getRunId(), this::publish);
                    var options = GenerateOptions.mergeOptions(GenerateOptions.builder().stream(true).build(), input.options());
                    return next.apply(new ModelCallInput(input.messages(), input.tools(), options, input.model()))
                            .doOnNext(event -> {
                                if (event instanceof TextBlockDeltaEvent text) {
                                    buffer.append(text.getDelta());
                                } else if (event instanceof ThinkingBlockDeltaEvent) {
                                    buffer.thinking();
                                }
                            }).doOnComplete(buffer::complete)
                            .doOnError(failure -> buffer.abort()).doOnCancel(buffer::abort);
                });
    }

    /**
     * 页面推送故障不能中止模型；只记录分类，临时文本允许丢失。
     */
    private void publish(com.agentsquad.squad.service.ReplyPreviewSignal signal) {
        try {
            previews.publishEvent(signal);
        } catch (RuntimeException unavailable) {
            log.debug("临时正文通知已丢弃 executionId={} errorType={}",
                    signal.preview().executionId(), unavailable.getClass().getSimpleName());
        }
    }

}
