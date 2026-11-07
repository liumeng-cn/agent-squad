package com.agentsquad.squad.service;

import com.agentsquad.squad.domain.entity.ExecutionFailureDO;
import com.agentsquad.squad.domain.entity.ExecutionScopeDO;
import com.agentsquad.squad.domain.entity.ProjectionTargetDO;
import com.agentsquad.squad.domain.exception.PlanCorrectionExhaustedException;
import com.agentsquad.squad.domain.model.ActivityBusinessFact;
import com.agentsquad.squad.domain.model.ModelFailureClassifier;

import com.agentsquad.squad.domain.model.FailureClassification;

import com.agentsquad.squad.mapper.ConversationMapper;
import com.agentsquad.squad.mapper.TaskMapper;
import com.agentsquad.squad.integration.agentscope.NativeSubmissionReceipt;
import com.agentsquad.squad.mapper.PlanMapper;
import com.agentsquad.squad.mapper.ProjectionMapper;
import com.agentsquad.squad.common.util.ContentDigests;
import com.agentsquad.squad.common.util.RuntimePayloads;
import com.agentsquad.squad.mapper.CommandMapper;
import com.agentsquad.squad.mapper.ExecutionScopeMapper;
import com.agentsquad.squad.mapper.ResponseRuntimeMapper;
import com.agentsquad.squad.mapper.TaskDirectiveMapper;
import com.agentsquad.squad.domain.dto.ExecutionFailureView;
import com.agentsquad.squad.mapper.ExecutionFailureMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 记录执行故障，并在次数限制内尝试自动恢复
 * <p>
 * 原始异常不进入公开事件；重试沿用原作用域、会话和预算，未知外部操作始终阻止自动执行。
 */
@Service
@Slf4j
public class ExecutionFailureService {
    @Resource
    private com.agentsquad.squad.service.ActivityProjectionService activities;
    @Resource
    private TaskMapper tasks;

    @Resource
    private ConversationMapper conversations;

    @Resource
    private PlanMapper plans;

    @Resource
    private ProjectionMapper projection;

    @Resource
    private TaskEventPublishingService taskEvents;

    @Resource
    private ExecutionScopeService scopes;

    @Resource
    private ExecutionScopeMapper scopeMapper;

    @Resource
    private ExecutionFailureMapper failures;

    @Resource
    private ConversationEventWriterService events;

    @Resource
    private CommandMapper commands;

    @Resource
    private ResponseRuntimeMapper responses;

    @Resource
    private TaskDirectiveMapper directives;

    @Resource
    private ObjectMapper json;

    /**
     * 模型错误先按可信类型分类并持久化，再允许原生失败事件终结；取消、旧计划和已结束执行不新增用户故障。
     */
    @Transactional
    public void reportModel(String userId, String sessionId, String key, Throwable error) {
        var scope = scopes.lockOwned(userId, scopes.scopeForSession(userId, sessionId), false);
        var owner = "TASK".equals(scope.kind()) ? scopeMapper.lockTaskOwner(userId, scope.ownerId(), scope.scopeId())
                : scopeMapper.lockResponseOwner(userId, scope.ownerId(), scope.scopeId());
        if (owner.cancellationRequested() || Set.of("SUCCEEDED", "PARTIAL", "FAILED", "CANCELLED").contains(owner.status())) {
            return;
        }
        String binding = scopeMapper.findBindingBySession(userId, sessionId);
        register(scope, binding, "model:" + key, ModelFailureClassifier.classify(error));
    }

    /**
     * 完成候选或原生终态有明确业务原因时使用固定分类，不能用异常文案推测权限。
     */
    @Transactional
    public void record(ExecutionScopeDO scope, String bindingId, String key, FailureClassification classification) {
        register(scope, bindingId, key, classification);
    }

    @Transactional
    public void ensureNativeFailure(ExecutionScopeDO scope, String bindingId, String key) {
        if (failures.latest(scope.userId(), scope.scopeId(), bindingId) == null) {
            register(scope, bindingId, key, new FailureClassification("INTERNAL", "NATIVE_RUN_INCOMPLETE", "SYSTEM", "本次处理未能完成，已保留你的内容，系统已记录问题。"));
        }
    }

    /**
     * 同一失败来源只建立一条恢复命令，次数、到期时间及命令原子提交；等待审批或未知写入时不安排重试。
     */
    @Transactional
    public boolean queueTransientRetry(ExecutionScopeDO scope, String bindingId, String eventId, String turnId) {
        scope = scopes.lockOwned(scope.userId(), scope.scopeId(), false);
        var owner = "TASK".equals(scope.kind()) ? scopeMapper.lockTaskOwner(scope.userId(), scope.ownerId(), scope.scopeId())
                : scopeMapper.lockResponseOwner(scope.userId(), scope.ownerId(), scope.scopeId());
        if (owner.cancellationRequested() || Set.of("SUCCEEDED", "PARTIAL", "FAILED", "CANCELLED").contains(owner.status())) {
            return false;
        }
        var failure = failures.latest(scope.userId(), scope.scopeId(), bindingId);
        if (failure == null || !"SERVICE_TRANSIENT".equals(failure.category())) {
            return false;
        }
        String key = "service-retry:" + scope.scopeId();
        if (commands.findByKeyCurrent(scope.userId(), key, eventId) != null) {
            return true;
        }
        if (!directives.openOperations(scope.userId(), scope.scopeId()).isEmpty()
                || !directives.openInteractions(scope.userId(), scope.scopeId()).isEmpty()) {
            return false;
        }
        int attempt = failures.serviceAttempts(scope.userId(), scope.scopeId());
        if (attempt >= 2) {
            failures.status(scope.userId(), failure.failureId(), "ACTIVE");
            return false;
        }
        LocalDateTime next = LocalDateTime.now(ZoneOffset.UTC).plusSeconds(1L << (attempt + 1));
        if (failures.reserveServiceRetry(scope.userId(), scope.scopeId(), next) != 1) {
            return false;
        }
        if ("RESPONSE".equals(scope.kind())) {
            failures.responseRetrying(scope.userId(), bindingId, next);
        }
        String payload = RuntimePayloads.toJson(json, Map.of("turnId", turnId));
        commands.insertControl(UUID.randomUUID().toString(), scope.userId(), key, eventId, ContentDigests.sha256(payload),
                "TASK".equals(scope.kind()) ? scope.ownerId() : null, bindingId, "RESUME", payload, next);
        failures.approveRetry(scope.userId(), failure.failureId(), attempt + 1);
        activities.completed(scope, bindingId, new com.agentsquad.squad.domain.model.ActivityBusinessFact(
                com.agentsquad.squad.common.enums.execution.ActivityType.AUTO_RETRY_SCHEDULED, eventId));
        events.publish(scope, "retry:" + failure.failureId() + ":" + (attempt + 1), "failure.updated", Map.of("failureId", failure.failureId(), "status", "RETRYING", "attempt", attempt + 1, "maxAttempts", 2, "nextAttemptAt", next.toString()));
        log.info("暂时服务故障已排队恢复 scopeId={} failureId={} attempt={} nextAttemptAt={}", scope.scopeId(), failure.failureId(), attempt + 1, next);
        return true;
    }

    /**
     * 发布工具的无效计划最多允许两次纠正；记录在原Scope，重启和换Turn不会重新获得次数。
     */
    @Transactional
    public boolean invalidPlan(String userId, String sessionId) {
        var scope = scopes.lockOwned(userId, scopes.scopeForSession(userId, sessionId), true);
        failures.invalidPlan(userId, scope.scopeId());
        int attempts = failures.planAttempts(userId, scope.scopeId());
        if (attempts > 2) {
            register(scope, scopeMapper.findBindingBySession(userId, sessionId), "plan-correction-exhausted",
                    new FailureClassification("LOGIC_INVALID", "PLAN_CORRECTION_EXHAUSTED", "SYSTEM", "本次处理方案未能通过校验，已保留你的要求和已完成内容。"));
        }
        return attempts <= 2;
    }

    /**
     * 只有已持久批准的暂时模型故障恢复可获得新计费尝试键；原请求的未知收费继续占额。
     */
    @Transactional
    public String modelRetrySuffix(String userId, String sessionId) {
        var scope = scopes.lockOwned(userId, scopes.scopeForSession(userId, sessionId), true);
        String bindingId = scopeMapper.findBindingBySession(userId, sessionId);
        var failure = failures.latest(userId, scope.scopeId(), bindingId);
        if (failure == null || !"RETRYING".equals(failure.status())
                || !"SERVICE_TRANSIENT".equals(failure.category()) || !failure.code().startsWith("MODEL_")) {
            return "";
        }
        return ":retry:" + failure.retryAttempt();
    }

    @Transactional
    public void requireModelAllowed(String userId, String sessionId) {
        var scope = scopes.lockOwned(userId, scopes.scopeForSession(userId, sessionId), true);
        if (failures.planAttempts(userId, scope.scopeId()) > 2) {
            throw new PlanCorrectionExhaustedException();
        }
    }

    @Transactional
    public void resolve(String userId, String bindingId) {
        String scopeId = scopeMapper.findScopeByBinding(userId, bindingId);
        var scope = scopes.lockOwned(userId, scopeId, false);
        if ("RESPONSE".equals(scope.kind())) {
            String previous = failures.predecessor(userId, scope.ownerId());
            java.util.Set<String> visited = new java.util.HashSet<>();
            while (previous != null && visited.add(previous)) {
                String previousBinding = responses.bindingForExecution(userId, previous);
                if (previousBinding != null) {
                    failures.resolveBinding(userId, previousBinding);
                }
                previous = failures.predecessor(userId, previous);
            }
        }
        if (failures.resolveBinding(userId, bindingId) > 0) {
            events.publish(scope, "failures-resolved:" + bindingId + ":" + UUID.randomUUID(), "failure.updated", Map.of("status", "RESOLVED"));
        }
    }

    /**
     * 同一会话已有新的有效回答，更早回复的失败提示不再指向当前状态。
     */
    @Transactional
    public void resolveSuperseded(ExecutionScopeDO scope) {
        var superseded = failures.activeResponseFailures(scope.userId(), scope.conversationId(),
                scope.conversationGeneration(), scope.scopeId());
        for (String failureId : superseded) {
            failures.status(scope.userId(), failureId, "RESOLVED");
        }
        if (!superseded.isEmpty()) {
            events.publish(scope, "failures-superseded:" + scope.scopeId() + ":" + UUID.randomUUID(),
                    "failure.updated", Map.of("status", "RESOLVED"));
            log.info("历史回复失败已被新回答取代 scopeId={} count={}", scope.scopeId(), superseded.size());
        }
    }

    public List<ExecutionFailureView> list(String userId, String conversationId) {
        return failures.activeInConversation(userId, conversationId).stream().map(row -> {
            var scope = scopeMapper.findOwned(userId, row.scopeId());
            String executionId = "RESPONSE".equals(scope.kind()) ? scope.ownerId() : null;
            String taskId = "TASK".equals(scope.kind()) ? scope.ownerId() : null;
            String source = failures.sourceMessage(userId, scope.scopeId());
            int attempt = failures.serviceAttemptsView(userId, scope.scopeId());
            var next = failures.nextAttempt(userId, scope.scopeId());
            List<String> actions = executionId != null && failures.retryAllowed(userId, executionId) ? List.of("RETRY") : List.of();
            if ("USER".equals(row.actor()) && "INPUT_REQUIRED".equals(row.category())
                    && failures.userActionAvailable(userId, row.bindingId(), "INPUT", LocalDateTime.now(ZoneOffset.UTC))) {
                actions = List.of("PROVIDE_INPUT");
            } else if ("USER".equals(row.actor()) && "AUTH_REQUIRED".equals(row.category())
                    && failures.userActionAvailable(userId, row.bindingId(), "APPROVAL", LocalDateTime.now(ZoneOffset.UTC))) {
                actions = List.of("APPROVE");
            }
            String message = "RETRYING".equals(row.status()) ? "回答服务暂时不可用，正在自动恢复（" + attempt + "/2）。" : row.safeMessage();
            if ("SERVICE_TRANSIENT".equals(row.category()) && "ACTIVE".equals(row.status()) && attempt >= 2) {
                message = "回答服务仍暂不可用，已暂停重试。你的消息和已完成内容已保留。";
            }
            return new ExecutionFailureView(row.failureId(), row.scopeId(), row.category(), row.code(), row.actor(), message,
                    actions.contains("RETRY"), attempt, 2, row.diagnosticId(), actions, executionId,
                    executionId == null ? null : failures.executionVersion(userId, executionId), taskId, source,
                    row.status(), next == null ? null : next.atOffset(ZoneOffset.UTC).toString());
        }).toList();
    }

    /**
     * SDK确认旧协调Turn中断后按原Turn恢复；持久次数和稳定命令键排除重启后的重复恢复。
     */
    @Transactional
    public void recoverInterrupted(ProjectionTargetDO target,
                                   NativeSubmissionReceipt receipt) {
        if (!"interrupted".equals(receipt.status()) || receipt.turnId() == null) {
            return;
        }
        var scope = scopes.lockOwned(target.userId(), scopeMapper.findScopeByBinding(target.userId(), target.bindingId()), false);
        var owner = scopeMapper.lockTaskOwner(scope.userId(), scope.ownerId(), scope.scopeId());
        if (owner.cancellationRequested() || !Set.of("RUNNING", "RECOVERY_BLOCKED", "RECONCILING").contains(owner.status())) {
            return;
        }
        String key = receipt.turnId() + ":" + receipt.runId();
        String scopeKey = "task-auto-resume:" + scope.ownerId();
        if (commands.findByKeyCurrent(scope.userId(), scopeKey, key) != null) {
            return;
        }
        if (!directives.openOperations(scope.userId(), scope.scopeId()).isEmpty()
                || !directives.openInteractions(scope.userId(), scope.scopeId()).isEmpty()) {
            return;
        }
        int attempt = failures.serviceAttempts(scope.userId(), scope.scopeId());
        if (attempt >= 2) {
            return;
        }
        LocalDateTime next = LocalDateTime.now(ZoneOffset.UTC).plusSeconds(1L << (attempt + 1));
        if (failures.reserveServiceRetry(scope.userId(), scope.scopeId(), next) != 1) {
            return;
        }
        register(scope, target.bindingId(), "interrupted:" + key, new FailureClassification("SERVICE_TRANSIENT", "COORDINATOR_INTERRUPTED", "SYSTEM", "处理曾暂时中断，正在从已保存的位置恢复。"));
        var failure = failures.latest(scope.userId(), scope.scopeId(), target.bindingId());
        failures.approveRetry(scope.userId(), failure.failureId(), attempt + 1);
        failures.recoverTask(scope.userId(), scope.ownerId());
        String payload = RuntimePayloads.toJson(json, Map.of("turnId", receipt.turnId()));
        commands.insertControl(UUID.randomUUID().toString(), scope.userId(), scopeKey, key, ContentDigests.sha256(payload), scope.ownerId(), target.bindingId(), "RESUME", payload, next);
        events.publish(scope, "task-resume:" + key, "failure.updated", Map.of("failureId", failure.failureId(), "status", "RETRYING", "attempt", attempt + 1));
        log.info("协调中断已按原Turn排队恢复 taskId={} bindingId={} attempt={}", scope.ownerId(), target.bindingId(), attempt + 1);
    }

    public String masterCommand(ProjectionTargetDO target) {
        return failures.latestMasterCommand(target.userId(), target.bindingId());
    }

    @Transactional
    public void resolveOperation(String userId, String scopeId, String operationId) {
        var scope = scopes.lockOwned(userId, scopeId, false);
        String id = failures.byKey(userId, scopeId, ContentDigests.sha256("operation:" + operationId));
        if (id != null) {
            failures.status(userId, id, "RESOLVED");
            events.publish(scope, "operation-resolved:" + operationId, "failure.updated", Map.of("failureId", id, "status", "RESOLVED"));
        }
    }

    @Transactional
    public void resolveInteraction(String userId, String scopeId, String interactionId) {
        var scope = scopes.lockOwned(userId, scopeId, false);
        String id = failures.byKey(userId, scopeId, ContentDigests.sha256("interaction:" + interactionId));
        if (id != null) {
            failures.status(userId, id, "RESOLVED");
            events.publish(scope, "interaction-resolved:" + interactionId, "failure.updated", Map.of("failureId", id, "status", "RESOLVED"));
        }
    }

    /**
     * 明确失败且无法再恢复的目标安全收尾；保留未知账本时拒绝收尾，旧节点及其命令同步失效。
     */
    @Transactional
    public boolean finishFailedTask(ExecutionScopeDO scope, String bindingId, String eventId) {
        if (!"TASK".equals(scope.kind())) {
            return false;
        }
        var failure = failures.latest(scope.userId(), scope.scopeId(), bindingId);
        if (failure == null || "NATIVE_RUN_INCOMPLETE".equals(failure.code())
                || "EXTERNAL_UNKNOWN".equals(failure.category()) || "USER".equals(failure.actor())) {
            return false;
        }
        if (!directives.openOperations(scope.userId(), scope.scopeId()).isEmpty()
                || !directives.openInteractions(scope.userId(), scope.scopeId()).isEmpty()) {
            return false;
        }
        var task = tasks.lockOwnedRow(scope.userId(), scope.ownerId());
        if (task == null || failures.failTask(scope.userId(), scope.ownerId()) != 1) {
            return false;
        }
        long barrier = task.planVersion() + 1;
        plans.supersedePlans(task.taskId(), barrier);
        plans.supersedeUnstartedJobs(task.taskId(), barrier);
        plans.barrierInFlightJobs(task.taskId(), barrier);
        plans.cancelSupersededCommands(task.taskId(), barrier);
        plans.cancelSupersededSignals(task.taskId());
        conversations.clearActiveTask(scope.userId(), scope.conversationId(), task.taskId());
        projection.reserveEventSequence(scope.userId(), task.taskId());
        taskEvents.insertProjected(scope.userId(), task.taskId(), projection.eventSequence(scope.userId(), task.taskId()),
                "failure-finalized:" + eventId, "task.completed", RuntimePayloads.toJson(json, Map.of("status", "FAILED", "version", task.version() + 1)), LocalDateTime.now(ZoneOffset.UTC));
        log.info("不可恢复故障已安全终结目标 taskId={} category={} diagnosticId={}", task.taskId(), failure.category(), failure.diagnosticId());
        return true;
    }

    public boolean canRetry(String userId, String executionId) {
        return failures.retryAllowed(userId, executionId);
    }

    private void register(ExecutionScopeDO scope, String bindingId, String key, FailureClassification classification) {
        String hash = ContentDigests.sha256(key);
        if (failures.byKey(scope.userId(), scope.scopeId(), hash) != null) {
            return;
        }
        // 同一作用域内同一原因只保留一条生效故障：工具校验与模型分类可能先后上报同一次终止。
        if (failures.countActiveByCode(scope.userId(), scope.scopeId(), classification.code()) > 0) {
            return;
        }
        var row = new ExecutionFailureDO(UUID.randomUUID().toString(), scope.scopeId(), bindingId, classification.category(), classification.code(), classification.actor(), classification.safeMessage(), UUID.randomUUID().toString(), "ACTIVE", 0);
        failures.insert(scope.userId(), row, hash);
        if ("RESPONSE".equals(scope.kind())) {
            failures.linkFailure(scope.userId(), bindingId, row.failureId());
        }
        events.publish(scope, "failure:" + row.failureId(), "failure.updated", Map.of("failureId", row.failureId(), "category", row.category(), "actor", row.actor(), "safeMessage", row.safeMessage(), "diagnosticId", row.diagnosticId()));
        if (key.startsWith("model:")) {
            // 记录安全错误码、用户可读原因与关联身份，不把供应商原始响应或密钥写入日志。
            log.error("模型调用失败 scopeId={} bindingId={} category={} code={} reason={} diagnosticId={}",
                    scope.scopeId(), bindingId, row.category(), row.code(), row.safeMessage(), row.diagnosticId());
        } else {
            log.warn("执行故障已记录 scopeId={} bindingId={} category={} code={} reason={} diagnosticId={}",
                    scope.scopeId(), bindingId, row.category(), row.code(), row.safeMessage(), row.diagnosticId());
        }
    }
}
