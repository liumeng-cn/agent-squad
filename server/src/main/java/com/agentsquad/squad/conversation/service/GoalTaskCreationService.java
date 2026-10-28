package com.agentsquad.squad.conversation.service;

import com.agentsquad.squad.artifact.ArtifactService;
import com.agentsquad.squad.common.exception.RuntimeBusinessException;
import com.agentsquad.squad.common.exception.RuntimeExceptionCode;
import com.agentsquad.squad.common.util.CanonicalJson;
import com.agentsquad.squad.common.util.RuntimePayloads;
import com.agentsquad.squad.conversation.domain.CommandRow;
import com.agentsquad.squad.conversation.domain.ConversationRow;
import com.agentsquad.squad.conversation.domain.ExecutionScopeRow;
import com.agentsquad.squad.conversation.domain.ResponseContextMessage;
import com.agentsquad.squad.conversation.domain.ResponseRuntimeRow;
import com.agentsquad.squad.conversation.dto.GoalTaskCreate;
import com.agentsquad.squad.conversation.dto.TaskReceipt;
import com.agentsquad.squad.conversation.repository.BindingMapper;
import com.agentsquad.squad.conversation.repository.CommandMapper;
import com.agentsquad.squad.conversation.repository.ConversationMapper;
import com.agentsquad.squad.conversation.repository.ExecutionScopeMapper;
import com.agentsquad.squad.conversation.repository.GoalCreationMapper;
import com.agentsquad.squad.conversation.repository.ResponseRuntimeMapper;
import com.agentsquad.squad.conversation.repository.TaskEventMapper;
import com.agentsquad.squad.conversation.repository.TaskMapper;
import com.agentsquad.squad.governance.domain.BudgetAccountRow;
import com.agentsquad.squad.governance.repository.BudgetMapper;
import com.agentsquad.squad.runtime.domain.MasterAgentIdentity;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 从受权回复创建独立目标的事务编排。
 * <p>
 * 服务端解析来源、共享预算和独立绑定；同逻辑调用重放返回原目标，不直接调用SDK或外部服务。
 */
@Service
@Slf4j
public class GoalTaskCreationService {
    @Resource
    private ExecutionScopeService scopes;

    @Resource
    private ExecutionScopeMapper scopeMapper;

    @Resource
    private ConversationMapper conversations;

    @Resource
    private ResponseRuntimeMapper replies;

    @Resource
    private ResponseContextService contexts;

    @Resource
    private GoalCreationMapper origins;

    @Resource
    private TaskMapper tasks;

    @Resource
    private BindingMapper bindings;

    @Resource
    private CommandMapper commands;

    @Resource
    private TaskEventMapper taskEvents;

    @Resource
    private ConversationEventWriter events;

    @Resource
    private BudgetMapper budgets;

    @Resource
    private ArtifactService artifacts;

    @Resource
    private ObjectMapper json;

    /**
     * 会话→来源回复→用户预算→账户顺序持锁，同事务创建目标、来源、Scope、Binding、命令与事件。
     * 首次创建检查运行状态；原调用重放仅返回原收据，不重新激活已结束目标或刷新额度。
     */
    @Transactional
    public TaskReceipt create(String userId, String sessionId, GoalTaskCreate request) {
        validate(request);
        ExecutionScopeRow source = scopes.lockOwned(userId, scopes.scopeForSession(userId, sessionId), false);
        if (!"RESPONSE".equals(source.kind())) {
            throw error(RuntimeExceptionCode.FORBIDDEN, "只有消息回复可以建立独立目标");
        }
        ConversationRow conversation = conversations.lockOwned(userId, source.conversationId());
        if (!"ACTIVE".equals(conversation.status()) || conversation.generation() != source.conversationGeneration()) {
            throw error(RuntimeExceptionCode.NOT_FOUND, "来源会话已失效");
        }
        String sourceBinding = scopeMapper.findBindingBySession(userId, sessionId);
        ResponseRuntimeRow reply = replies.lockByBinding(userId, sourceBinding);
        if (reply == null || !reply.sourceMessageId().equals(request.sourceMessageId())) {
            throw error(RuntimeExceptionCode.FORBIDDEN, "目标来源必须是当前受权消息");
        }
        String scopeKey = "goal.create:" + source.scopeId();
        String requestJson = RuntimePayloads.toJson(json, request);
        String hash = CanonicalJson.sha256(json, requestJson);
        CommandRow prior = commands.findByKeyCurrent(userId, scopeKey, request.logicalKey());
        if (prior != null) {
            if (!hash.equals(prior.payloadHash())) {
                throw error(RuntimeExceptionCode.CONFLICT, "同一目标创建键的内容不一致");
            }
            return new TaskReceipt(prior.taskId(), prior.commandId(), "QUEUED");
        }
        if (reply.cancellationRequested() || !Set.of("QUEUED", "RUNNING", "RECOVERING").contains(reply.status())) {
            throw error(RuntimeExceptionCode.CONFLICT, "当前回复已不能创建目标");
        }
        if (conversation.activeTaskId() != null) {
            throw error(RuntimeExceptionCode.CONFLICT, "会话已有未完成目标，请先处理当前目标");
        }

        ResponseContextMessage message = contexts.freeze(userId, sourceBinding).messages().getLast();
        budgets.ensureUser(userId);
        budgets.lockUser(userId);
        BudgetAccountRow account = budgets.lockAccount(userId, source.scopeId());
        if (account == null) {
            throw error(RuntimeExceptionCode.CONFLICT, "来源预算账户已不可用");
        }
        String taskId = UUID.randomUUID().toString();
        String bindingId = UUID.randomUUID().toString();
        String commandId = UUID.randomUUID().toString();
        String scopeId = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        // 用户原文和模型拟定的目标分别保存，不能把模型改写标记成用户原话。
        String input = RuntimePayloads.toJson(json, Map.of("message", message.text(), "title", request.title(),
                "objective", request.objective(), "sourceMessageId", request.sourceMessageId(),
                "artifactIds", message.artifactIds()));
        tasks.insert(taskId, userId, source.conversationId(), input, account.limitMicrounits());
        scopeMapper.insertScope(scopeId, userId, source.conversationId(), source.conversationGeneration(),
                "TASK", taskId, source.accountId());
        if (scopeMapper.linkTaskScope(userId, taskId, scopeId) != 1
                || origins.linkSource(userId, taskId, message.messageId(), reply.executionId()) != 1) {
            throw new IllegalStateException("目标来源或执行归属绑定失败");
        }
        artifacts.attach(userId, taskId, message.artifactIds());
        bindings.insertMaster(bindingId, userId, taskId, MasterAgentIdentity.AGENT_ID, UUID.randomUUID().toString(), now);
        String initialMessage = "请推进已经建立的独立目标，可使用计划与Worker能力。不要再次创建目标。"
                + "以下JSON分别标记目标说明与受权用户原文，不包含整段会话：\n" + input;
        String payload = RuntimePayloads.toJson(json, Map.of("message", initialMessage, "sourceMessageId", message.messageId()));
        commands.insertSubmit(commandId, userId, scopeKey, request.logicalKey(), hash, taskId, bindingId, payload, now);
        taskEvents.insertAccepted(userId, taskId, "command:" + commandId,
                RuntimePayloads.toJson(json, Map.of("taskId", taskId, "commandId", commandId)), now);
        if (conversations.activateTask(userId, source.conversationId(), taskId) != 1) {
            throw error(RuntimeExceptionCode.CONFLICT, "会话目标占用已变化");
        }
        events.publish(source, "task-created:" + taskId, "task.created", Map.of("taskId", taskId,
                "sourceMessageId", message.messageId(), "sourceExecutionId", reply.executionId(),
                "title", request.title(), "status", "QUEUED"));
        log.info("Master独立目标创建事实已写入 conversationId={} executionId={} taskId={} commandId={}",
                source.conversationId(), reply.executionId(), taskId, commandId);
        return new TaskReceipt(taskId, commandId, "QUEUED");
    }

    private void validate(GoalTaskCreate request) {
        if (request == null || invalid(request.logicalKey(), 160) || invalid(request.title(), 200)
                || invalid(request.objective(), 20000) || invalid(request.sourceMessageId(), 36)) {
            throw error(RuntimeExceptionCode.VALIDATION, "目标创建参数无效");
        }
        try {
            UUID.fromString(request.sourceMessageId());
        } catch (IllegalArgumentException exception) {
            throw error(RuntimeExceptionCode.VALIDATION, "来源消息标识无效");
        }
    }

    private boolean invalid(String value, int max) {
        return value == null || value.isBlank() || value.length() > max;
    }

    private RuntimeBusinessException error(RuntimeExceptionCode code, String message) {
        return new RuntimeBusinessException(code, message);
    }
}
