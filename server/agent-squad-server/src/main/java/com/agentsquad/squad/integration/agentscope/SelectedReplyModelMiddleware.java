package com.agentsquad.squad.integration.agentscope;

import com.agentsquad.squad.conversation.repository.ResponseRuntimeMapper;
import com.agentsquad.squad.model.domain.AcceptedModelChoice;
import com.agentsquad.squad.model.domain.ReasoningMode;
import com.agentsquad.squad.model.service.ModelCatalogService;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.middleware.ModelCallInput;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import jakarta.annotation.Resource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * 普通回复的固定模型路由
 * <p>
 * 每次调用从持久选择恢复模型与模式，不修改共享Master；旧回复和独立目标保持原路径，Worker继续复用同一目录。
 */
@Component
@ConditionalOnProperty(prefix = "agentsquad.delivery", name = "enabled", havingValue = "true")
public class SelectedReplyModelMiddleware implements MiddlewareBase {
    private final java.util.concurrent.ConcurrentHashMap<String, CachedReplyModel> clients = new java.util.concurrent.ConcurrentHashMap<>();

    @Resource
    private ResponseRuntimeMapper replies;

    @Resource
    private ModelCatalogService catalog;

    @Override
    public Set<ExtensionPoint> activePoints() {
        return EnumSet.of(ExtensionPoint.ON_MODEL_CALL);
    }

    @Override
    public Flux<AgentEvent> onModelCall(Agent agent, RuntimeContext context, ModelCallInput input,
                                        Function<ModelCallInput, Flux<AgentEvent>> next) {
        return Mono.fromCallable(() -> Optional.ofNullable(replies.modelChoiceBySession(context.getUserId(), context.getSessionId())))
                .subscribeOn(Schedulers.boundedElastic()).flatMapMany(value -> {
                    if (value.isEmpty()) {
                        context.put(AcceptedModelChoice.class, null);
                        return next.apply(input);
                    }
                    AcceptedModelChoice choice = catalog.decode(value.get());
                    var profile = catalog.resolve(choice);
                    context.put(AcceptedModelChoice.class, choice);
                    var options = GenerateOptions.builder().modelName(profile.getModelName());
                    if (profile.getReasoningAdapter() == com.agentsquad.squad.model.domain.ReasoningAdapter.SPARK_THINKING) {
                        options.maxCompletionTokens(profile.getMaxOutputTokens());
                    } else {
                        options.maxTokens(profile.getMaxOutputTokens());
                    }
                    if (choice.reasoningMode() != ReasoningMode.DEFAULT) {
                        switch (profile.getReasoningAdapter()) {
                            case SPARK_THINKING ->
                                    options.additionalBodyParam("thinking", Map.of("type", switch (choice.reasoningMode()) {
                                        case OFF -> "disabled";
                                        case AUTO -> "auto";
                                        case ON -> "enabled";
                                        default -> throw new IllegalStateException("已受理思考模式与协议不匹配");
                                    }));
                            case OPENAI_EFFORT ->
                                    options.reasoningEffort(choice.reasoningMode().name().toLowerCase(java.util.Locale.ROOT));
                            case NONE -> throw new IllegalStateException("该模型未声明思考参数协议");
                        }
                    }
                    String stamp = choice.configurationVersion() + com.agentsquad.squad.common.util.ContentDigests.sha256(profile.getApiKey());
                    var client = clients.compute(choice.modelId(), (id, previous) -> previous != null && previous.configurationStamp().equals(stamp)
                            ? previous : new CachedReplyModel(stamp, OpenAIChatModel.builder().apiKey(profile.getApiKey()).baseUrl(profile.getBaseUrl())
                            .modelName(profile.getModelName()).stream(false).build()));
                    return next.apply(new ModelCallInput(input.messages(), input.tools(),
                            GenerateOptions.mergeOptions(options.build(), input.options()), client.model()));
                });
    }
}
