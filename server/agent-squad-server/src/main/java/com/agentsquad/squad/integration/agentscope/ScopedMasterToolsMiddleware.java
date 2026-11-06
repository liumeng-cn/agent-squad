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
    private static final Set<String> TASK_ONLY = Set.of("prepare_context", "publish_plan", "apply_task_directive", "abandon_failed_job", "request_acceptance_review");
    private static final Set<String> RESPONSE_ONLY = Set.of("create_goal_task", "submit_task_directive", "write_explicit_memory");

    @Resource
    private com.agentsquad.squad.conversation.repository.ResponseRuntimeMapper replies;

    @Resource
    private ExecutionScopeService scopes;

    @Resource
    private ExecutionScopeMapper scopeMapper;

    @Resource
    private com.agentsquad.squad.conversation.repository.TaskMapper tasks;

    @Resource
    private com.agentsquad.squad.runtime.repository.PlanMapper plans;

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
                    boolean natural = response && replies.completionProtocolBySession(context.getUserId(), context.getSessionId())
                            == com.agentsquad.squad.conversation.domain.ResponseCompletionProtocol.NATURAL;
                    var hidden = new java.util.HashSet<>(response ? TASK_ONLY : RESPONSE_ONLY);
                    if (natural) {
                        hidden.add("propose_completion");
                    }
                    var task = response ? null : tasks.findOwned(context.getUserId(), scope.ownerId());
                    boolean awaitingWorkers = task != null && task.planVersion() > 0
                            && plans.countActiveJobs(context.getUserId(), task.taskId(), task.planVersion()) > 0;
                    if (awaitingWorkers) {
                        hidden.add("propose_completion");
                        hidden.add("request_acceptance_review");
                    }
                    String roleInstruction = response
                            ? "当前职责是回复这条用户消息。如果已用create_goal_task接下独立工作，本次只用一两句话确认正在处理；"
                            + "等待后台工作是正常过程，不要称为失败、缺口或尚不能交付，不解释执行状态细节。"
                            + "不要主动提供跳过协作的替代方案，不再要求用户回复一句来确认继续。"
                            + "除非确实缺少用户才能提供的关键材料，否则自行推进。查询进展时只给简洁的真实进展。"
                            : "当前是独立目标的执行协调会话，目标已经创建，不是面向用户的受理回复。"
                            + "必须在本轮实际调用工具推进工作，不能只回答收到、正在创建、需要执行或将会处理。"
                            + "目标要求Worker协作时，先调用list_worker_capabilities查询能力及详情，"
                            + "为每个节点调用prepare_context取得快照，再调用publish_plan启动执行；并行节点不设置相互依赖。"
                            + "工具失败时依据错误修正参数继续执行，确实无法推进时调用propose_completion说明实际失败。"
                            + "发布计划后直接结束这一轮，不在当前轮次等待后台结果；平台稍后通知。"
                            + "完成时finalAnswer默认只放可直接使用的成品，不要放任务受理、两步协作、执行说明或验收报告。"
                            + "原始用户未明确要求展示提纲或过程时，不公开内部提纲，也不解释如何依据提纲。"
                            + "交付前检查篇幅和内容要求，超长则压缩。不要擅自添加用户未要求的成果。";
                    if (awaitingWorkers) {
                        roleInstruction = "当前计划已经发布，Worker仍在执行，等待结果是正常状态。"
                                + "若没有收到TASK_DIRECTIVE或明确的节点失败通知，本轮直接输出简短等待说明并结束，"
                                + "不要再调用工具，不要重新发布计划、检查验收或声明完成。平台会在结果就绪后另起一轮通知你。"
                                + "只有收到TASK_DIRECTIVE时才调用apply_task_directive处理用户修改。";
                    } else if (task != null && task.planVersion() > 0) {
                        roleInstruction = "当前计划的Worker已经收尾，这是结果核验与最终交付阶段，不是初次派工。"
                                + "先读取本轮通知携带的节点结果与验收条件，已有成果满足用户要求时不要重新发布计划。"
                                + "仅USER_REVIEW条件调用request_acceptance_review并等待用户确认；MODEL_REVIEW引用本计划成功jobId如实评价，"
                                + "PROGRAMMATIC使用平台检查事实。随后调用propose_completion提交完整finalAnswer及逐项证据。"
                                + "只有确实存在未满足条件或用户修改了目标时才重新准备上下文和计划，不能因为收到汇合通知就再派一遍相同工作。"
                                + "最终回答只包含用户需要的成品，不输出内部执行报告。";
                    }
                    if (natural) {
                        roleInstruction += "当前回复采用自然输出协议，直接给出面向用户的答案正文，不调用完成声明工具，也不解释系统回执。";
                    }
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
