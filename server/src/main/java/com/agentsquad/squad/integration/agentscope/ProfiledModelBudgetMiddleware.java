package com.agentsquad.squad.integration.agentscope;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.middleware.ModelCallInput;
import reactor.core.publisher.Flux;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.Function;

/**
 * 绑定单个不可变 Worker Definition 的模型 Profile 预算中间件。
 */
public class ProfiledModelBudgetMiddleware implements MiddlewareBase {

    private final ModelBudgetMiddleware budgetMiddleware;

    private final String profileKey;

    public ProfiledModelBudgetMiddleware(ModelBudgetMiddleware budgetMiddleware, String profileKey) {
        this.budgetMiddleware = budgetMiddleware;
        this.profileKey = profileKey;
    }

    @Override
    public Set<ExtensionPoint> activePoints() {
        return EnumSet.of(ExtensionPoint.ON_MODEL_CALL);
    }

    @Override
    public Flux<AgentEvent> onModelCall(Agent agent, RuntimeContext context, ModelCallInput input,
                                        Function<ModelCallInput, Flux<AgentEvent>> next) {
        return budgetMiddleware.onModelCallForProfile(profileKey, context, input, next);
    }
}
