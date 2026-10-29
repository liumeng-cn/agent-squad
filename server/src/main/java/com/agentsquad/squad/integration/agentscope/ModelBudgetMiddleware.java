package com.agentsquad.squad.integration.agentscope;

import com.agentsquad.squad.runtime.service.ExecutionFailureService;
import com.agentsquad.squad.common.util.ContentDigests;
import com.agentsquad.squad.governance.BudgetService;
import com.agentsquad.squad.governance.repository.BudgetMapper;
import com.agentsquad.squad.worker.profile.WorkerModelProfile;
import com.agentsquad.squad.worker.profile.WorkerProfileRegistry;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.ModelCallEndEvent;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.middleware.ModelCallInput;
import io.agentscope.core.model.ChatUsage;
import io.agentscope.core.session.SessionRecorder;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * 原生模型调用的执行预算闸门。
 *
 * <p>请求发出前按会话归属锁定执行所有者与共享账户 并预留最坏成本；只有可信用量才能结算。
 * 错误、缺失用量和取消保留原预留，避免恢复时把可能收费的调用重新计为免费。</p>
 */
@Component
@Slf4j
@ConditionalOnProperty(prefix = "agentsquad.delivery", name = "enabled", havingValue = "true")
public class ModelBudgetMiddleware implements MiddlewareBase {

    @Resource
    private ExecutionFailureService failures;

    @Resource
    private BudgetMapper budgetMapper;

    @Resource
    private BudgetService budgetService;

    @Resource
    private WorkerProfileRegistry profileRegistry;

    @Resource
    private ObjectMapper objectMapper;

    @Override
    public Set<ExtensionPoint> activePoints() {
        return EnumSet.of(ExtensionPoint.ON_MODEL_CALL);
    }

    /**
     * 同一原生 Turn 的同一请求内容使用稳定键；重复投递发现旧预留即拒绝再次请求模型。
     */
    @Override
    public Flux<AgentEvent> onModelCall(Agent agent, RuntimeContext context, ModelCallInput input,
                                        Function<ModelCallInput, Flux<AgentEvent>> next) {
        return onModelCallForProfile("default", context, input, next);
    }

    /**
     * WorkerFactory 可用固定 Definition 的模型 Profile 复用同一预留边界。
     */
    public Flux<AgentEvent> onModelCallForProfile(String profileKey, RuntimeContext context,
                                                   ModelCallInput input,
                                                   Function<ModelCallInput, Flux<AgentEvent>> next) {
        return Flux.defer(() -> {
            String userId = context.getUserId();
            String sessionId = context.getSessionId();
            String turnId = context.get(SessionRecorder.TURN_ID_KEY, String.class);
            if (userId == null || sessionId == null || turnId == null || turnId.isBlank()) {
                return Flux.error(new IllegalStateException("模型调用缺少固定会话或原生回合"));
            }
            WorkerModelProfile profile = profileRegistry.requireExecutableModel(profileKey);
            String requestHash = requestHash(input, profileKey);
            return Mono.fromCallable(() -> failures.modelRetrySuffix(userId, sessionId))
                    .subscribeOn(Schedulers.boundedElastic()).flatMapMany(retrySuffix -> {
                        String usageKey = "model:" + turnId + ":" + requestHash + retrySuffix;
                        AtomicBoolean settled = new AtomicBoolean(false);
                        return Mono.fromCallable(() -> {
                                    failures.requireModelAllowed(userId, sessionId);
                                    String scopeId = budgetMapper.findScopeBySession(userId, sessionId);
                                    if (scopeId == null) {
                                        throw new IllegalStateException("模型会话未绑定执行作用域");
                                    }
                                    budgetService.reserveModel(userId, scopeId, usageKey, requestHash,
                                            profileKey, profile);
                                    return scopeId;
                                })
                                .subscribeOn(Schedulers.boundedElastic())
                                .flatMapMany(scopeId -> next.apply(input)
                                        .concatMap(event -> {
                                            if (!(event instanceof ModelCallEndEvent endEvent)) {
                                                return Mono.just(event);
                                            }
                                            ChatUsage usage = endEvent.getUsage();
                                            if (usage == null) {
                                                return Mono.error(new IllegalStateException("模型响应缺少可信用量"));
                                            }
                                            return Mono.fromCallable(() -> {
                                                        budgetService.settleModel(userId, scopeId, usageKey,
                                                                profile, usage.getInputTokens(), usage.getOutputTokens());
                                                        settled.set(true);
                                                        return event;
                                                    })
                                                    .subscribeOn(Schedulers.boundedElastic());
                                        })
                                        .onErrorResume(error -> markUnknown(userId, scopeId, usageKey)
                                                .thenMany(Flux.error(error)))
                                        .concatWith(Flux.defer(() -> settled.get() ? Flux.empty()
                                                : markUnknown(userId, scopeId, usageKey).thenMany(Flux.error(
                                                new IllegalStateException("模型调用结束但没有用量终结事件")))))
                                        .doOnCancel(() -> {
                                            if (!settled.get()) {
                                                markUnknown(userId, scopeId, usageKey).subscribe();
                                            }
                                        }))
                                .onErrorResume(error -> Mono.fromRunnable(() -> failures.reportModel(userId, sessionId, usageKey, error))
                                        .subscribeOn(Schedulers.boundedElastic()).thenMany(Flux.error(error)));
                    });
        });
    }

    private String requestHash(ModelCallInput input, String profileKey) {
        try {
            String request = profileKey + "\n" + objectMapper.writeValueAsString(input.messages())
                    + "\n" + objectMapper.writeValueAsString(input.tools())
                    + "\n" + objectMapper.writeValueAsString(input.options());
            return ContentDigests.sha256(request);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("模型请求无法计算稳定摘要", exception);
        }
    }

    private Mono<Void> markUnknown(String userId, String scopeId, String usageKey) {
        return Mono.fromRunnable(() -> {
                    budgetService.markModelUnknown(userId, scopeId, usageKey);
                    log.warn("模型收费待核对 scopeId={} usageKey={}", scopeId, usageKey);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }
}
