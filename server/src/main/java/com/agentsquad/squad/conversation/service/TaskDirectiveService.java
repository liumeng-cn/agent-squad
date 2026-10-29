package com.agentsquad.squad.conversation.service;

import com.agentsquad.squad.artifact.ArtifactMapper;
import com.agentsquad.squad.artifact.ArtifactService;
import com.agentsquad.squad.common.exception.RuntimeBusinessException;
import com.agentsquad.squad.common.exception.RuntimeExceptionCode;
import com.agentsquad.squad.common.util.CanonicalJson;
import com.agentsquad.squad.common.util.ContentDigests;
import com.agentsquad.squad.common.util.RuntimePayloads;
import com.agentsquad.squad.conversation.domain.ConversationRow;
import com.agentsquad.squad.conversation.domain.ExecutionScopeRow;
import com.agentsquad.squad.conversation.domain.ResponseRuntimeRow;
import com.agentsquad.squad.conversation.domain.TaskDirectiveRow;
import com.agentsquad.squad.conversation.domain.TaskDirectiveTarget;
import com.agentsquad.squad.conversation.domain.TaskGoalState;
import com.agentsquad.squad.conversation.domain.TaskRow;
import com.agentsquad.squad.conversation.dto.TaskDirectiveSubmit;
import com.agentsquad.squad.conversation.dto.TaskDirectiveDecision;
import com.agentsquad.squad.conversation.dto.TaskDirectiveReceipt;
import com.agentsquad.squad.conversation.repository.BindingMapper;
import com.agentsquad.squad.conversation.repository.CommandMapper;
import com.agentsquad.squad.conversation.repository.ConversationMapper;
import com.agentsquad.squad.conversation.repository.ExecutionScopeMapper;
import com.agentsquad.squad.conversation.repository.ResponseRuntimeMapper;
import com.agentsquad.squad.conversation.repository.TaskDirectiveMapper;
import com.agentsquad.squad.conversation.repository.TaskMapper;
import com.agentsquad.squad.runtime.domain.PlanRow;
import com.agentsquad.squad.runtime.repository.PlanMapper;
import com.agentsquad.squad.runtime.service.MasterSignalService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 用户目标修改的受理、协调与应用边界。
 * <p>
 * 回复Master只保存建议，任务Master在安全点作出决定；原预算与SDK绑定不更换，旧计划在应用事务中失效。
 */
@Service
@Slf4j
public class TaskDirectiveService {
    private static final Set<String> TERMINAL = Set.of("SUCCEEDED", "PARTIAL", "FAILED", "CANCELLED");

    @Resource
    private ExecutionScopeService scopes;

    @Resource
    private ExecutionScopeMapper scopeMapper;

    @Resource
    private ConversationMapper conversations;

    @Resource
    private TaskMapper tasks;

    @Resource
    private ResponseRuntimeMapper replies;

    @Resource
    private ResponseContextService contexts;

    @Resource
    private TaskDirectiveMapper directives;

    @Resource
    private BindingMapper bindings;

    @Resource
    private CommandMapper commands;

    @Resource
    private PlanMapper plans;

    @Resource
    private MasterSignalService signals;

    @Resource
    private ConversationEventWriter events;

    @Resource
    private ArtifactMapper artifactMapper;

    @Resource
    private ArtifactService artifacts;

    @Resource
    private ObjectMapper json;

    /**
     * 当前消息与目标归属、版本同事务校验；同键重放不重复生成协调命令，受理不代表已经应用。
     */
    @Transactional
    public TaskDirectiveReceipt submit(String userId, String sessionId, TaskDirectiveSubmit request) {
        validate(request);
        ExecutionScopeRow source = scopes.lockOwned(userId, scopes.scopeForSession(userId, sessionId), false);
        if (!"RESPONSE".equals(source.kind())) {
            throw error(RuntimeExceptionCode.FORBIDDEN, "只有消息回复可以提交目标修改");
        }
        ConversationRow conversation = conversations.lockOwned(userId, source.conversationId());
        requireConversation(source, conversation);
        String hash = CanonicalJson.sha256(json, RuntimePayloads.toJson(json, request));
        TaskDirectiveRow prior = directives.findByKey(userId, source.scopeId(), request.logicalKey());
        if (prior != null) {
            if (!hash.equals(prior.payloadHash())) {
                throw error(RuntimeExceptionCode.CONFLICT, "相同修改键的内容不一致");
            }
            return receipt(prior);
        }
        ResponseRuntimeRow reply = replies.lockByBinding(userId, scopeMapper.findBindingBySession(userId, sessionId));
        if (reply == null || reply.cancellationRequested() || !Set.of("QUEUED", "RUNNING", "RECOVERING").contains(reply.status())) {
            throw error(RuntimeExceptionCode.CONFLICT, "当前回复已不能提交修改");
        }
        if (!request.taskId().equals(conversation.activeTaskId())) {
            throw error(RuntimeExceptionCode.NOT_FOUND, "目标不是当前会话的在途目标");
        }
        TaskRow task = tasks.lockOwnedRow(userId, request.taskId());
        if (task == null || !source.conversationId().equals(task.conversationId()) || task.cancelRequestedAt() != null || TERMINAL.contains(task.status())) {
            throw error(RuntimeExceptionCode.CONFLICT, "目标已停止或结束");
        }
        if (task.version() != request.expectedVersion()) {
            throw error(RuntimeExceptionCode.CONFLICT, "目标版本已变化，当前版本为" + task.version());
        }
        var snapshot = contexts.freeze(userId, reply.bindingId());
        TaskGoalState goal = directives.goalState(userId, task.taskId());
        if (snapshot.activeTask() == null || !task.taskId().equals(snapshot.activeTask().taskId())
                || snapshot.activeTask().goalRevision() != goal.goalRevision()) {
            throw error(RuntimeExceptionCode.CONFLICT, "目标内容已变化，请确认当前目标后再提交修改");
        }
        var message = snapshot.messages().getLast();
        TaskDirectiveRow row = new TaskDirectiveRow(UUID.randomUUID().toString(), userId, source.conversationId(),
                source.conversationGeneration(), message.messageId(), source.scopeId(), task.taskId(), request.logicalKey(), hash,
                task.version(), goal.goalRevision(), request.kind(), request.content(), message.text(),
                RuntimePayloads.toJson(json, message.artifactIds()), "ACCEPTED", null, null, null, null, null);
        directives.insert(row, now());
        publish(source, row, "ACCEPTED", null, null, null, null);
        log.info("目标修改已持久受理 directiveId={} taskId={} sourceMessageId={} kind={} expectedVersion={}",
                row.directiveId(), task.taskId(), row.sourceMessageId(), row.kind(), row.expectedVersion());
        return receipt(row);
    }

    /**
     * 独立任务Master在当前Turn内应用或拒绝；目标语义变化阻断旧计划，等待中的外部操作须先明确结果。
     */
    @Transactional
    public TaskDirectiveReceipt decide(String userId, String sessionId, TaskDirectiveDecision request) {
        if (request == null || invalid(request.directiveId(), 36) || request.expectedVersion() < 0
                || request.reason() != null && request.reason().length() > 500
                || request.decision() == null || !Set.of("APPLY", "REJECT").contains(request.decision())
                || ("REJECT".equals(request.decision()) && invalid(request.reason(), 500))) {
            throw error(RuntimeExceptionCode.VALIDATION, "修改决定参数无效");
        }
        ExecutionScopeRow target = scopes.lockOwned(userId, scopes.scopeForSession(userId, sessionId), false);
        if (!"TASK".equals(target.kind()) || !scopeMapper.findBindingBySession(userId, sessionId)
                .equals(bindings.findMasterBindingId(userId, target.ownerId()))) {
            throw error(RuntimeExceptionCode.FORBIDDEN, "只有目标协调会话可以应用修改");
        }
        requireConversation(target, conversations.lockOwned(userId, target.conversationId()));
        TaskRow task = tasks.lockOwnedRow(userId, target.ownerId());
        TaskDirectiveRow row = directives.lock(userId, request.directiveId());
        if (row == null || !task.taskId().equals(row.taskId()) || row.generation() != target.conversationGeneration()) {
            throw error(RuntimeExceptionCode.NOT_FOUND, "修改指令不存在或不属于当前目标");
        }
        if (!"ACCEPTED".equals(row.status())) {
            return receipt(row);
        }
        if (task.cancelRequestedAt() != null || TERMINAL.contains(task.status())) {
            return reject(target, task, row, "TASK_ENDED", "目标已停止，修改未应用");
        }
        TaskGoalState goal = directives.goalState(userId, task.taskId());
        if (goal.goalRevision() != row.expectedGoalRevision()) {
            return reject(target, task, row, "GOAL_CHANGED", "目标内容已被其他修改更新，请重新确认本次要求");
        }
        if (task.version() != request.expectedVersion()) {
            throw error(RuntimeExceptionCode.CONFLICT, "目标版本已变化，当前版本为" + task.version());
        }
        if ("REJECT".equals(request.decision())) {
            return reject(target, task, row, "COORDINATOR_REJECTED", request.reason());
        }
        if (!Set.of("QUEUED", "RUNNING").contains(task.status()) || hasOpenEffects(target)) {
            throw error(RuntimeExceptionCode.CONFLICT, "当前操作或交互尚未处理完成，修改仍为已收到，暂不应用");
        }
        List<String> files = artifactIds(row);
        for (String id : files) {
            if (artifactMapper.findOwned(userId, id) == null) {
                return reject(target, task, row, "MATERIAL_UNAVAILABLE", "补充材料已失效，请重新添加材料");
            }
        }
        List<TaskDirectiveRow> effective = "REPLACE".equals(row.kind()) ? List.of() : directives.effectiveApplied(userId, task.taskId());
        long size = original(goal.inputJson()).length() + row.sourceText().length()
                + effective.stream().mapToLong(item -> item.sourceText().length()).sum();
        if (size > 200000) {
            return reject(target, task, row, "CONTEXT_LIMIT", "修改后的材料超过当前处理范围，请精简材料");
        }
        ObjectNode input = object(goal.inputJson());
        if ("REPLACE".equals(row.kind())) {
            input.put("objective", row.content());
        }
        input.put("goalRevision", goal.goalRevision() + 1);
        if (directives.applyGoal(userId, task.taskId(), task.version(), goal.goalRevision(), RuntimePayloads.toJson(json, input)) != 1) {
            throw error(RuntimeExceptionCode.CONFLICT, "应用修改时目标版本已变化");
        }
        // 仍保留旧计划和证据，但它们不能再激活依赖、提交结果或复用旧材料进入新计划。
        long barrierVersion = task.planVersion() + 1;
        plans.supersedePlans(task.taskId(), barrierVersion);
        plans.supersedeUnstartedJobs(task.taskId(), barrierVersion);
        plans.barrierInFlightJobs(task.taskId(), barrierVersion);
        plans.cancelSupersededCommands(task.taskId(), barrierVersion);
        plans.cancelSupersededSignals(task.taskId());
        var existingFiles = artifactMapper.listTaskIds(userId, task.taskId());
        artifacts.attach(userId, task.taskId(), files.stream().filter(id -> !existingFiles.contains(id)).toList());
        directives.finish(userId, row.directiveId(), "APPLIED", task.version() + 1, goal.goalRevision() + 1, null, null);
        publish(target, row, "APPLIED", task.version() + 1, goal.goalRevision() + 1, null, null);
        events.publish(target, "goal-change:" + row.directiveId(), "task.goal_changed", Map.of(
                "taskId", task.taskId(), "version", task.version() + 1, "goalRevision", goal.goalRevision() + 1,
                "sourceMessageId", row.sourceMessageId()));
        for (String pending : directives.pending(userId, task.taskId())) {
            TaskDirectiveRow other = directives.lock(userId, pending);
            if (other.expectedGoalRevision() != goal.goalRevision() + 1) {
                reject(target, task, other, "GOAL_CHANGED", "目标内容已变化，本次并发修改未应用，请重新确认");
            }
        }
        log.info("目标修改已应用 directiveId={} taskId={} goalRevision={} version={}", row.directiveId(), task.taskId(), goal.goalRevision() + 1, task.version() + 1);
        return receipt(directives.lock(userId, row.directiveId()));
    }

    /**
     * 在前序任务Turn已投影、无开放效果的安全点排入协调命令；扫描与数据库提交不跨模型等待。
     */
    @Transactional
    public void dispatch(TaskDirectiveTarget candidate) {
        String userId = candidate.userId();
        ExecutionScopeRow target = scopes.lockOwned(userId, scopes.scopeForTask(userId, candidate.taskId()), false);
        TaskRow task = tasks.lockOwnedRow(userId, candidate.taskId());
        TaskDirectiveRow row = directives.lock(userId, candidate.directiveId());
        if (row == null || !"ACCEPTED".equals(row.status())) {
            return;
        }
        ConversationRow conversation = conversations.lockOwned(userId, target.conversationId());
        if (!"ACTIVE".equals(conversation.status()) || conversation.generation() != row.generation()
                || task.cancelRequestedAt() != null || TERMINAL.contains(task.status())) {
            reject(target, task, row, "TASK_ENDED", "目标已停止或会话已失效，修改未应用");
            return;
        }
        TaskGoalState goal = directives.goalState(userId, task.taskId());
        if (goal.goalRevision() != row.expectedGoalRevision()) {
            reject(target, task, row, "GOAL_CHANGED", "目标内容已变化，请重新确认本次修改");
            return;
        }
        directives.defer(userId, row.directiveId(), now().plusSeconds(2));
        if (!Set.of("QUEUED", "RUNNING").contains(task.status()) || hasOpenEffects(target)
                || commands.countUnprocessedMasterCommands(userId, task.taskId()) > 0
                || directives.pendingControls(userId, task.taskId()) > 0) {
            return;
        }
        if (row.commandId() != null) {
            var command = commands.findOwned(userId, row.commandId());
            if (command != null && Set.of("APPLIED", "BLOCKED", "CANCELLED").contains(command.status())) {
                reject(target, task, row, "NO_DECISION", "此次修改未得到有效处理，原要求已保留，请重新确认");
            }
            return;
        }
        String first = directives.pending(userId, task.taskId()).getFirst();
        if (!first.equals(row.directiveId())) {
            return;
        }
        directives.clearCandidate(userId, task.taskId());
        String binding = bindings.findMasterBindingId(userId, task.taskId());
        String commandId = UUID.randomUUID().toString();
        String message = "用户对当前目标提出修改。只在本目标协调会话处理，先调用apply_task_directive作出APPLY或REJECT决定。"
                + "ACCEPTED仅表示收到，未应用不能声称生效；有待审批或结果未知的操作先处理，不能重放未知写入。"
                + "应用后必须用新目标修订号准备新的局部上下文并重发计划，旧结果不是新目标的交付。参数与来源原文：\n"
                + RuntimePayloads.toJson(json, Map.of("directiveId", row.directiveId(), "expectedVersion", task.version(),
                "kind", row.kind(), "content", row.content(), "userMessage", row.sourceText()));
        String payload = RuntimePayloads.toJson(json, Map.of("message", message, "directiveId", row.directiveId()));
        commands.insertControl(commandId, userId, "directive-coordinate:" + row.directiveId(), "initial",
                ContentDigests.sha256(payload), task.taskId(), binding, "TASK_DIRECTIVE", payload, now());
        directives.bindCommand(userId, row.directiveId(), commandId);
        log.info("目标修改协调已排队 directiveId={} taskId={} commandId={}", row.directiveId(), task.taskId(), commandId);
    }

    private boolean hasOpenEffects(ExecutionScopeRow scope) {
        return !directives.openOperations(scope.userId(), scope.scopeId()).isEmpty()
                || !directives.openInteractions(scope.userId(), scope.scopeId()).isEmpty();
    }

    private TaskDirectiveReceipt reject(ExecutionScopeRow scope, TaskRow task, TaskDirectiveRow row, String code, String reason) {
        directives.finish(scope.userId(), row.directiveId(), "REJECTED", null, null, code, reason);
        publish(scope, row, "REJECTED", null, null, code, reason);
        if (task.cancelRequestedAt() == null && !TERMINAL.contains(task.status()) && task.planVersion() > 0) {
            PlanRow plan = plans.lockPlan(scope.userId(), task.taskId(), task.planVersion());
            if (plan != null && "ACTIVE".equals(plan.status())) {
                signals.onJobStateChanged(plan);
            }
        }
        log.info("目标修改未应用 directiveId={} taskId={} reasonCode={}", row.directiveId(), row.taskId(), code);
        return receipt(directives.lock(scope.userId(), row.directiveId()));
    }

    private void publish(ExecutionScopeRow scope, TaskDirectiveRow row, String status, Long version, Long revision, String code, String reason) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("directiveId", row.directiveId());
        payload.put("taskId", row.taskId());
        payload.put("sourceMessageId", row.sourceMessageId());
        payload.put("status", status);
        if (version != null) {
            payload.put("appliedVersion", version);
            payload.put("goalRevision", revision);
        }
        if (code != null) {
            payload.put("reasonCode", code);
            payload.put("reasonMessage", reason);
        }
        events.publish(scope, "directive:" + row.directiveId() + ":" + status, "directive.status_changed", payload);
    }

    private TaskDirectiveReceipt receipt(TaskDirectiveRow row) {
        return new TaskDirectiveReceipt(row.directiveId(), row.taskId(), row.sourceMessageId(), row.status(),
                row.appliedVersion(), row.appliedGoalRevision(), row.reasonCode(), row.reasonMessage());
    }

    private List<String> artifactIds(TaskDirectiveRow row) {
        try {
            return java.util.Arrays.asList(json.treeToValue(RuntimePayloads.readJson(json, row.artifactIdsJson()), String[].class));
        } catch (Exception exception) {
            throw new IllegalStateException("修改材料引用无法读取", exception);
        }
    }

    private ObjectNode object(String value) {
        try {
            return (ObjectNode) RuntimePayloads.readJson(json, value);
        } catch (Exception exception) {
            throw new IllegalStateException("目标输入无法读取", exception);
        }
    }

    private String original(String value) {
        return object(value).path("message").asText();
    }

    private void requireConversation(ExecutionScopeRow scope, ConversationRow conversation) {
        if (!"ACTIVE".equals(conversation.status()) || conversation.generation() != scope.conversationGeneration()) {
            throw error(RuntimeExceptionCode.NOT_FOUND, "会话已失效");
        }
    }

    private void validate(TaskDirectiveSubmit request) {
        if (request == null || invalid(request.logicalKey(), 160) || invalid(request.taskId(), 36)
                || request.expectedVersion() < 0 || request.kind() == null || !Set.of("SUPPLEMENT", "REPLACE").contains(request.kind())
                || invalid(request.content(), 20000)) {
            throw error(RuntimeExceptionCode.VALIDATION, "修改请求参数无效");
        }
    }

    private boolean invalid(String value, int max) {
        return value == null || value.isBlank() || value.length() > max;
    }

    private RuntimeBusinessException error(RuntimeExceptionCode code, String message) {
        return new RuntimeBusinessException(code, message);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}
