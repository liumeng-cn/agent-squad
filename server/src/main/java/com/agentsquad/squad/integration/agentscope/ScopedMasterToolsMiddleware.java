package com.agentsquad.squad.integration.agentscope;

import com.agentsquad.squad.conversation.repository.ExecutionScopeMapper;
import com.agentsquad.squad.conversation.service.ExecutionScopeService;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.middleware.ModelCallInput;
import jakarta.annotation.Resource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.Function;

/**
 * Master模型目录的执行角色裁剪。
 * 回复仅能提交目标及修订，任务协调才展示计划写入能力；底层工具仍独立校验权限，目录裁剪不替代鉴权。
 */
@Component
@ConditionalOnProperty(prefix = "agentsquad.delivery", name = "enabled", havingValue = "true")
public class ScopedMasterToolsMiddleware implements MiddlewareBase {
    private static final Set<String> TASK_ONLY = Set.of("prepare_context", "publish_plan", "apply_task_directive");
    private static final Set<String> RESPONSE_ONLY = Set.of("create_goal_task", "submit_task_directive", "write_explicit_memory");

    @Resource
    private ExecutionScopeService scopes;

    @Resource
    private ExecutionScopeMapper scopeMapper;

    @Override
    public Set<ExtensionPoint> activePoints() {
        return EnumSet.of(ExtensionPoint.ON_MODEL_CALL);
    }

    /**
     * 在预算摘要与模型请求前裁剪目录；固定Session确定角色，不从用户正文推断，也不改变已受理SDK输入。
     */
    @Override
    public Flux<AgentEvent> onModelCall(Agent agent, RuntimeContext context, ModelCallInput input,
                                        Function<ModelCallInput, Flux<AgentEvent>> next) {
        return Mono.fromCallable(() -> {
                    String scopeId = scopes.scopeForSession(context.getUserId(), context.getSessionId());
                    var scope = scopeMapper.findOwned(context.getUserId(), scopeId);
                    boolean response = "RESPONSE".equals(scope.kind());
                    var hidden = response ? TASK_ONLY : RESPONSE_ONLY;
                    String roleInstruction = response
                            ? "当前职责是回复这条用户消息。如果已用create_goal_task接下独立工作，本次只用一两句话确认正在处理；"
                            + "等待后台工作是正常过程，不要称为失败、缺口或尚不能交付，不解释执行状态细节。"
                            + "不要主动提供跳过协作的替代方案，不再要求用户回复一句来确认继续。"
                            + "除非确实缺少用户才能提供的关键材料，否则自行推进。查询进展时只给简洁的真实进展。"
                            : "当前职责是推进已经接下的目标。发布计划后直接结束这一轮，不在当前轮次等待后台结果；平台稍后通知。"
                            + "完成时finalAnswer默认只放可直接使用的成品，不要放任务受理、两步协作、执行说明或验收报告。"
                            + "原始用户未明确要求展示提纲或过程时，不公开内部提纲，也不解释如何依据提纲。"
                            + "交付前检查篇幅和内容要求，超长则压缩。不要擅自添加用户未要求的成果。";
                    var messages = new ArrayList<>(input.messages());
                    int position = 0;
                    while (position < messages.size() && messages.get(position).getRole() == MsgRole.SYSTEM) {
                        position++;
                    }
                    messages.add(position, Msg.builder().role(MsgRole.SYSTEM).textContent(roleInstruction).build());
                    var tools = input.tools().stream().filter(tool -> !hidden.contains(tool.getName())).toList();
                    context.put(com.agentsquad.squad.skill.integration.ScopeSkillCapabilities.class,
                            new com.agentsquad.squad.skill.integration.ScopeSkillCapabilities(
                                    tools.stream().map(tool -> tool.getName()).collect(java.util.stream.Collectors.toSet())));
                    return new ModelCallInput(messages, tools, input.options(), input.model());
                }).subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(next);
    }
}
