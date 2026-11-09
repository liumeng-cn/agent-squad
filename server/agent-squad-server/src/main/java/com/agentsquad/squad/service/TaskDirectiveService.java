package com.agentsquad.squad.service;

import com.agentsquad.squad.mapper.ArtifactMapper;
import com.agentsquad.squad.enums.TaskDirectiveWaitReason;
import com.agentsquad.squad.vo.TaskDirectiveVO;
import com.agentsquad.squad.exceptions.RuntimeBusinessException;
import com.agentsquad.squad.enums.RuntimeExceptionCode;
import com.agentsquad.squad.utils.CanonicalJson;
import com.agentsquad.squad.utils.ContentDigests;
import com.agentsquad.squad.utils.RuntimePayloads;
import com.agentsquad.squad.po.ConversationPO;
import com.agentsquad.squad.po.ExecutionScopePO;
import com.agentsquad.squad.po.ResponseRuntimePO;
import com.agentsquad.squad.po.TaskDirectivePO;
import com.agentsquad.squad.bo.TaskDirectiveTargetBO;
import com.agentsquad.squad.bo.TaskGoalStateBO;
import com.agentsquad.squad.po.TaskPO;
import com.agentsquad.squad.bo.TaskDirectiveSubmitBO;
import com.agentsquad.squad.bo.TaskDirectiveDecisionBO;
import com.agentsquad.squad.bo.TaskDirectiveReceiptBO;
import com.agentsquad.squad.mapper.BindingMapper;
import com.agentsquad.squad.mapper.CommandMapper;
import com.agentsquad.squad.mapper.ConversationMapper;
import com.agentsquad.squad.mapper.ExecutionScopeMapper;
import com.agentsquad.squad.mapper.ResponseRuntimeMapper;
import com.agentsquad.squad.mapper.TaskDirectiveMapper;
import com.agentsquad.squad.mapper.TaskMapper;
import com.agentsquad.squad.po.PlanPO;
import com.agentsquad.squad.mapper.PlanMapper;
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
 * 受理用户目标修改请求，协调执行并应用修改
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
    private ConversationEventWriterService events;

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
    public TaskDirectiveReceiptBO submit(String userId, String sessionId, TaskDirectiveSubmitBO request) {
        validate(request);
        ExecutionScopePO source = scopes.lockOwned(userId, scopes.scopeForSession(userId, sessionId), false);
        if (!"RESPONSE".equals(source.getKind())) {
            throw error(RuntimeExceptionCode.FORBIDDEN, "只有消息回复可以提交目标修改");
        }
        ConversationPO conversation = conversations.lockOwned(userId, source.getConversationId());
        requireConversation(source, conversation);
        String hash = CanonicalJson.sha256(json, RuntimePayloads.toJson(json, request));
        TaskDirectivePO prior = directives.findByKey(userId, source.getScopeId(), request.logicalKey());
        if (prior != null) {
            if (!hash.equals(prior.getPayloadHash())) {
                throw error(RuntimeExceptionCode.CONFLICT, "相同修改键的内容不一致");
            }
            return receipt(prior);
        }
        ResponseRuntimePO reply = replies.lockByBinding(userId, scopeMapper.findBindingBySession(userId, sessionId));
        if (reply == null || reply.isCancellationRequested() || !Set.of("QUEUED", "RUNNING", "RECOVERING").contains(reply.getStatus())) {
            throw error(RuntimeExceptionCode.CONFLICT, "当前回复已不能提交修改");
        }
        if (!request.taskId().equals(conversation.getActiveTaskId())) {
            throw error(RuntimeExceptionCode.NOT_FOUND, "目标不是当前会话的在途目标");
        }
        TaskPO task = tasks.lockOwnedRow(userId, request.taskId());
        if (task == null || !source.getConversationId().equals(task.getConversationId()) || task.getCancelRequestedAt() != null || TERMINAL.contains(task.getStatus())) {
            throw error(RuntimeExceptionCode.CONFLICT, "目标已停止或结束");
        }
        if (task.getVersion() != request.expectedVersion()) {
            throw error(RuntimeExceptionCode.CONFLICT, "目标版本已变化，当前版本为" + task.getVersion());
        }
        var snapshot = contexts.freeze(userId, reply.getBindingId());
        TaskGoalStateBO goal = directives.goalState(userId, task.getTaskId());
        if (snapshot.activeTask() == null || !task.getTaskId().equals(snapshot.activeTask().taskId())
                || snapshot.activeTask().goalRevision() != goal.getGoalRevision()) {
            throw error(RuntimeExceptionCode.CONFLICT, "目标内容已变化，请确认当前目标后再提交修改");
        }
        var message = snapshot.messages().getLast();
        TaskDirectivePO row = new TaskDirectivePO(UUID.randomUUID().toString(), userId, source.getConversationId(),
                source.getConversationGeneration(), message.messageId(), source.getScopeId(), task.getTaskId(), request.logicalKey(), hash,
                task.getVersion(), goal.getGoalRevision(), request.kind(), request.content(), message.text(),
                RuntimePayloads.toJson(json, message.artifactIds()), "ACCEPTED", null, null, null, null, null);
        directives.insert(row, now());
        publish(source, row, "ACCEPTED", null, null, null, null);
        log.info("目标修改已持久受理 directiveId={} taskId={} sourceMessageId={} kind={} expectedVersion={}",
                row.getDirectiveId(), task.getTaskId(), row.getSourceMessageId(), row.getKind(), row.getExpectedVersion());
        return receipt(row);
    }

    /**
     * 独立任务Master在当前Turn内应用或拒绝；目标语义变化阻断旧计划，等待中的外部操作须先明确结果。
     */
    @Transactional
    public TaskDirectiveReceiptBO decide(String userId, String sessionId, TaskDirectiveDecisionBO request) {
        if (request == null || invalid(request.directiveId(), 36) || request.expectedVersion() < 0
                || request.reason() != null && request.reason().length() > 500
                || request.decision() == null || !Set.of("APPLY", "REJECT").contains(request.decision())
                || ("REJECT".equals(request.decision()) && invalid(request.reason(), 500))) {
            throw error(RuntimeExceptionCode.VALIDATION, "修改决定参数无效");
        }
        ExecutionScopePO target = scopes.lockOwned(userId, scopes.scopeForSession(userId, sessionId), false);
        if (!"TASK".equals(target.getKind()) || !scopeMapper.findBindingBySession(userId, sessionId)
                .equals(bindings.findMasterBindingId(userId, target.getOwnerId()))) {
            throw error(RuntimeExceptionCode.FORBIDDEN, "只有目标协调会话可以应用修改");
        }
        requireConversation(target, conversations.lockOwned(userId, target.getConversationId()));
        TaskPO task = tasks.lockOwnedRow(userId, target.getOwnerId());
        TaskDirectivePO row = directives.lock(userId, request.directiveId());
        if (row == null || !task.getTaskId().equals(row.getTaskId()) || row.getGeneration() != target.getConversationGeneration()) {
            throw error(RuntimeExceptionCode.NOT_FOUND, "修改指令不存在或不属于当前目标");
        }
        if (!"ACCEPTED".equals(row.getStatus())) {
            return receipt(row);
        }
        if (task.getCancelRequestedAt() != null || TERMINAL.contains(task.getStatus())) {
            return reject(target, task, row, "TASK_ENDED", "目标已停止，修改未应用");
        }
        TaskGoalStateBO goal = directives.goalState(userId, task.getTaskId());
        if (goal.getGoalRevision() != row.getExpectedGoalRevision()) {
            return reject(target, task, row, "GOAL_CHANGED", "目标内容已被其他修改更新，请重新确认本次要求");
        }
        if (task.getVersion() != request.expectedVersion()) {
            throw error(RuntimeExceptionCode.CONFLICT, "目标版本已变化，当前版本为" + task.getVersion());
        }
        if ("REJECT".equals(request.decision())) {
            return reject(target, task, row, "COORDINATOR_REJECTED", request.reason());
        }
        if (!Set.of("QUEUED", "RUNNING").contains(task.getStatus()) || hasOpenEffects(target)) {
            throw error(RuntimeExceptionCode.CONFLICT, "当前操作或交互尚未处理完成，修改仍为已收到，暂不应用");
        }
        List<String> files = artifactIds(row);
        for (String id : files) {
            if (artifactMapper.findOwned(userId, id) == null) {
                return reject(target, task, row, "MATERIAL_UNAVAILABLE", "补充材料已失效，请重新添加材料");
            }
        }
        List<TaskDirectivePO> effective = "REPLACE".equals(row.getKind()) ? List.of() : directives.effectiveApplied(userId, task.getTaskId());
        long size = original(goal.getInputJson()).length() + row.getSourceText().length()
                + effective.stream().mapToLong(item -> item.getSourceText().length()).sum();
        if (size > 200000) {
            return reject(target, task, row, "CONTEXT_LIMIT", "修改后的材料超过当前处理范围，请精简材料");
        }
        ObjectNode input = object(goal.getInputJson());
        if ("REPLACE".equals(row.getKind())) {
            input.put("objective", row.getContent());
        }
        input.put("goalRevision", goal.getGoalRevision() + 1);
        if (directives.applyGoal(userId, task.getTaskId(), task.getVersion(), goal.getGoalRevision(), RuntimePayloads.toJson(json, input)) != 1) {
            throw error(RuntimeExceptionCode.CONFLICT, "应用修改时目标版本已变化");
        }
        // 仍保留旧计划和证据，但它们不能再激活依赖、提交结果或复用旧材料进入新计划。
        long barrierVersion = task.getPlanVersion() + 1;
        plans.supersedePlans(task.getTaskId(), barrierVersion);
        plans.supersedeUnstartedJobs(task.getTaskId(), barrierVersion);
        plans.barrierInFlightJobs(task.getTaskId(), barrierVersion);
        plans.cancelSupersededCommands(task.getTaskId(), barrierVersion);
        plans.cancelSupersededSignals(task.getTaskId());
        var existingFiles = artifactMapper.listTaskIds(userId, task.getTaskId());
        artifacts.attach(userId, task.getTaskId(), files.stream().filter(id -> !existingFiles.contains(id)).toList());
        directives.finish(userId, row.getDirectiveId(), "APPLIED", task.getVersion() + 1, goal.getGoalRevision() + 1, null, null);
        publish(target, row, "APPLIED", task.getVersion() + 1, goal.getGoalRevision() + 1, null, null);
        events.publish(target, "goal-change:" + row.getDirectiveId(), "task.goal_changed", Map.of(
                "taskId", task.getTaskId(), "version", task.getVersion() + 1, "goalRevision", goal.getGoalRevision() + 1,
                "sourceMessageId", row.getSourceMessageId()));
        for (String pending : directives.pending(userId, task.getTaskId())) {
            TaskDirectivePO other = directives.lock(userId, pending);
            if (other.getExpectedGoalRevision() != goal.getGoalRevision() + 1) {
                reject(target, task, other, "GOAL_CHANGED", "目标内容已变化，本次并发修改未应用，请重新确认");
            }
        }
        log.info("目标修改已应用 directiveId={} taskId={} goalRevision={} version={}", row.getDirectiveId(), task.getTaskId(), goal.getGoalRevision() + 1, task.getVersion() + 1);
        return receipt(directives.lock(userId, row.getDirectiveId()));
    }

    /**
     * 在前序任务Turn已投影、无开放效果的安全点排入协调命令；扫描与数据库提交不跨模型等待。
     */
    @Transactional
    public void dispatch(TaskDirectiveTargetBO candidate) {
        String userId = candidate.getUserId();
        ExecutionScopePO target = scopes.lockOwned(userId, scopes.scopeForTask(userId, candidate.getTaskId()), false);
        TaskPO task = tasks.lockOwnedRow(userId, candidate.getTaskId());
        TaskDirectivePO row = directives.lock(userId, candidate.getDirectiveId());
        if (row == null || !"ACCEPTED".equals(row.getStatus())) {
            return;
        }
        ConversationPO conversation = conversations.lockOwned(userId, target.getConversationId());
        if (!"ACTIVE".equals(conversation.getStatus()) || conversation.getGeneration() != row.getGeneration()
                || task.getCancelRequestedAt() != null || TERMINAL.contains(task.getStatus())) {
            reject(target, task, row, "TASK_ENDED", "目标已停止或会话已失效，修改未应用");
            return;
        }
        TaskGoalStateBO goal = directives.goalState(userId, task.getTaskId());
        if (goal.getGoalRevision() != row.getExpectedGoalRevision()) {
            reject(target, task, row, "GOAL_CHANGED", "目标内容已变化，请重新确认本次修改");
            return;
        }
        directives.defer(userId, row.getDirectiveId(), now().plusSeconds(2));
        if (!directives.openOperations(userId, target.getScopeId()).isEmpty()) {
            waiting(target, row, TaskDirectiveWaitReason.WAITING_OPERATION);
            return;
        }
        if (!directives.openInteractions(userId, target.getScopeId()).isEmpty()) {
            waiting(target, row, TaskDirectiveWaitReason.WAITING_USER);
            return;
        }
        if (!Set.of("QUEUED", "RUNNING").contains(task.getStatus())
                || commands.countUnprocessedMasterCommands(userId, task.getTaskId()) > 0
                || directives.pendingControls(userId, task.getTaskId()) > 0) {
            waiting(target, row, TaskDirectiveWaitReason.WAITING_COORDINATOR);
            return;
        }
        if (row.getCommandId() != null) {
            var command = commands.findOwned(userId, row.getCommandId());
            if (command != null && Set.of("APPLIED", "BLOCKED", "CANCELLED").contains(command.getStatus())) {
                reject(target, task, row, "NO_DECISION", "此次修改未得到有效处理，原要求已保留，请重新确认");
            }
            return;
        }
        String first = directives.pending(userId, task.getTaskId()).getFirst();
        if (!first.equals(row.getDirectiveId())) {
            return;
        }
        directives.clearCandidate(userId, task.getTaskId());
        String binding = bindings.findMasterBindingId(userId, task.getTaskId());
        String commandId = UUID.randomUUID().toString();
        String message = "用户对当前目标提出修改。只在本目标协调会话处理，先调用apply_task_directive作出APPLY或REJECT决定。"
                + "ACCEPTED仅表示收到，未应用不能声称生效；有待审批或结果未知的操作先处理，不能重放未知写入。"
                + "应用后必须用新目标修订号准备新的局部上下文并重发计划，旧结果不是新目标的交付。参数与来源原文：\n"
                + RuntimePayloads.toJson(json, Map.of("directiveId", row.getDirectiveId(), "expectedVersion", task.getVersion(),
                "kind", row.getKind(), "content", row.getContent(), "userMessage", row.getSourceText()));
        String payload = RuntimePayloads.toJson(json, Map.of("message", message, "directiveId", row.getDirectiveId()));
        commands.insertControl(commandId, userId, "directive-coordinate:" + row.getDirectiveId(), "initial",
                ContentDigests.sha256(payload), task.getTaskId(), binding, "TASK_DIRECTIVE", payload, now());
        directives.bindCommand(userId, row.getDirectiveId(), commandId);
        waiting(target, row, TaskDirectiveWaitReason.COORDINATING);
        log.info("目标修改协调已排队 directiveId={} taskId={} commandId={}", row.getDirectiveId(), task.getTaskId(), commandId);
    }

    /** 等待原因只在实际变化时写入并通知；重复扫描不产生重复进度事件。 */
    private void waiting(ExecutionScopePO scope, TaskDirectivePO row, TaskDirectiveWaitReason reason) {
        if (directives.waiting(scope.getUserId(), row.getDirectiveId(), reason) == 1) {
            publish(scope, row, "ACCEPTED", null, null, reason.name(), null);
        }
    }

    private boolean hasOpenEffects(ExecutionScopePO scope) {
        return !directives.openOperations(scope.getUserId(), scope.getScopeId()).isEmpty()
                || !directives.openInteractions(scope.getUserId(), scope.getScopeId()).isEmpty();
    }

    private TaskDirectiveReceiptBO reject(ExecutionScopePO scope, TaskPO task, TaskDirectivePO row, String code, String reason) {
        directives.finish(scope.getUserId(), row.getDirectiveId(), "REJECTED", null, null, code, reason);
        publish(scope, row, "REJECTED", null, null, code, reason);
        if (task.getCancelRequestedAt() == null && !TERMINAL.contains(task.getStatus()) && task.getPlanVersion() > 0) {
            PlanPO plan = plans.lockPlan(scope.getUserId(), task.getTaskId(), task.getPlanVersion());
            if (plan != null && "ACTIVE".equals(plan.getStatus())) {
                signals.onJobStateChanged(plan);
            }
        }
        log.info("目标修改未应用 directiveId={} taskId={} reasonCode={}", row.getDirectiveId(), row.getTaskId(), code);
        return receipt(directives.lock(scope.getUserId(), row.getDirectiveId()));
    }

    private void publish(ExecutionScopePO scope, TaskDirectivePO row, String status, Long version, Long revision, String code, String reason) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", 2);
        payload.put("directive", TaskDirectiveVO.from(directives.lock(scope.getUserId(), row.getDirectiveId())));
        payload.put("directiveId", row.getDirectiveId());
        payload.put("taskId", row.getTaskId());
        payload.put("sourceMessageId", row.getSourceMessageId());
        payload.put("status", status);
        if (version != null) {
            payload.put("appliedVersion", version);
            payload.put("goalRevision", revision);
        }
        if (code != null) {
            payload.put("reasonCode", code);
            payload.put("reasonMessage", reason);
        }
        events.publish(scope, "directive:" + row.getDirectiveId() + ":" + status + ":" + UUID.randomUUID(), "directive.status_changed", payload);
    }

    private TaskDirectiveReceiptBO receipt(TaskDirectivePO row) {
        return new TaskDirectiveReceiptBO(row.getDirectiveId(), row.getTaskId(), row.getSourceMessageId(), row.getStatus(),
                row.getAppliedVersion(), row.getAppliedGoalRevision(), row.getReasonCode(), row.getReasonMessage());
    }

    private List<String> artifactIds(TaskDirectivePO row) {
        try {
            return java.util.Arrays.asList(json.treeToValue(RuntimePayloads.readJson(json, row.getArtifactIdsJson()), String[].class));
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

    private void requireConversation(ExecutionScopePO scope, ConversationPO conversation) {
        if (!"ACTIVE".equals(conversation.getStatus()) || conversation.getGeneration() != scope.getConversationGeneration()) {
            throw error(RuntimeExceptionCode.NOT_FOUND, "会话已失效");
        }
    }

    private void validate(TaskDirectiveSubmitBO request) {
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
