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
        var owner = "TASK".equals(scope.getKind()) ? scopeMapper.lockTaskOwner(userId, scope.getOwnerId(), scope.getScopeId())
                : scopeMapper.lockResponseOwner(userId, scope.getOwnerId(), scope.getScopeId());
        if (owner.isCancellationRequested() || Set.of("SUCCEEDED", "PARTIAL", "FAILED", "CANCELLED").contains(owner.getStatus())) {
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
        if (failures.latest(scope.getUserId(), scope.getScopeId(), bindingId) == null) {
            register(scope, bindingId, key, new FailureClassification("INTERNAL", "NATIVE_RUN_INCOMPLETE", "SYSTEM", "本次处理未能完成，已保留你的内容，系统已记录问题。"));
        }
    }

    /**
     * 同一失败来源只建立一条恢复命令，次数、到期时间及命令原子提交；等待审批或未知写入时不安排重试。
     */
    @Transactional
    public boolean queueTransientRetry(ExecutionScopeDO scope, String bindingId, String eventId, String turnId) {
        scope = scopes.lockOwned(scope.getUserId(), scope.getScopeId(), false);
        var owner = "TASK".equals(scope.getKind()) ? scopeMapper.lockTaskOwner(scope.getUserId(), scope.getOwnerId(), scope.getScopeId())
                : scopeMapper.lockResponseOwner(scope.getUserId(), scope.getOwnerId(), scope.getScopeId());
        if (owner.isCancellationRequested() || Set.of("SUCCEEDED", "PARTIAL", "FAILED", "CANCELLED").contains(owner.getStatus())) {
            return false;
        }
        var failure = failures.latest(scope.getUserId(), scope.getScopeId(), bindingId);
        if (failure == null || !"SERVICE_TRANSIENT".equals(failure.getCategory())) {
            return false;
        }
        String key = "service-retry:" + scope.getScopeId();
        if (commands.findByKeyCurrent(scope.getUserId(), key, eventId) != null) {
            return true;
        }
        if (!directives.openOperations(scope.getUserId(), scope.getScopeId()).isEmpty()
                || !directives.openInteractions(scope.getUserId(), scope.getScopeId()).isEmpty()) {
            return false;
        }
        int attempt = failures.serviceAttempts(scope.getUserId(), scope.getScopeId());
        if (attempt >= 2) {
            failures.status(scope.getUserId(), failure.getFailureId(), "ACTIVE");
            return false;
        }
        LocalDateTime next = LocalDateTime.now(ZoneOffset.UTC).plusSeconds(1L << (attempt + 1));
        if (failures.reserveServiceRetry(scope.getUserId(), scope.getScopeId(), next) != 1) {
            return false;
        }
        if ("RESPONSE".equals(scope.getKind())) {
            failures.responseRetrying(scope.getUserId(), bindingId, next);
        }
        String payload = RuntimePayloads.toJson(json, Map.of("turnId", turnId));
        commands.insertControl(UUID.randomUUID().toString(), scope.getUserId(), key, eventId, ContentDigests.sha256(payload),
                "TASK".equals(scope.getKind()) ? scope.getOwnerId() : null, bindingId, "RESUME", payload, next);
        failures.approveRetry(scope.getUserId(), failure.getFailureId(), attempt + 1);
        activities.completed(scope, bindingId, new com.agentsquad.squad.domain.model.ActivityBusinessFact(
                com.agentsquad.squad.common.enums.execution.ActivityType.AUTO_RETRY_SCHEDULED, eventId));
        events.publish(scope, "retry:" + failure.getFailureId() + ":" + (attempt + 1), "failure.updated", Map.of("failureId", failure.getFailureId(), "status", "RETRYING", "attempt", attempt + 1, "maxAttempts", 2, "nextAttemptAt", next.toString()));
        log.info("暂时服务故障已排队恢复 scopeId={} failureId={} attempt={} nextAttemptAt={}", scope.getScopeId(), failure.getFailureId(), attempt + 1, next);
        return true;
    }

    /**
     * 发布工具的无效计划最多允许两次纠正；记录在原Scope，重启和换Turn不会重新获得次数。
     */
    @Transactional
    public boolean invalidPlan(String userId, String sessionId) {
        var scope = scopes.lockOwned(userId, scopes.scopeForSession(userId, sessionId), true);
        failures.invalidPlan(userId, scope.getScopeId());
        int attempts = failures.planAttempts(userId, scope.getScopeId());
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
        var failure = failures.latest(userId, scope.getScopeId(), bindingId);
        if (failure == null || !"RETRYING".equals(failure.getStatus())
                || !"SERVICE_TRANSIENT".equals(failure.getCategory()) || !failure.getCode().startsWith("MODEL_")) {
            return "";
        }
        return ":retry:" + failure.getRetryAttempt();
    }

    @Transactional
    public void requireModelAllowed(String userId, String sessionId) {
        var scope = scopes.lockOwned(userId, scopes.scopeForSession(userId, sessionId), true);
        if (failures.planAttempts(userId, scope.getScopeId()) > 2) {
            throw new PlanCorrectionExhaustedException();
        }
    }

    @Transactional
    public void resolve(String userId, String bindingId) {
        String scopeId = scopeMapper.findScopeByBinding(userId, bindingId);
        var scope = scopes.lockOwned(userId, scopeId, false);
        if ("RESPONSE".equals(scope.getKind())) {
            String previous = failures.predecessor(userId, scope.getOwnerId());
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
        var superseded = failures.activeResponseFailures(scope.getUserId(), scope.getConversationId(),
                scope.getConversationGeneration(), scope.getScopeId());
        for (String failureId : superseded) {
            failures.status(scope.getUserId(), failureId, "RESOLVED");
        }
        if (!superseded.isEmpty()) {
            events.publish(scope, "failures-superseded:" + scope.getScopeId() + ":" + UUID.randomUUID(),
                    "failure.updated", Map.of("status", "RESOLVED"));
            log.info("历史回复失败已被新回答取代 scopeId={} count={}", scope.getScopeId(), superseded.size());
        }
    }

    public List<ExecutionFailureView> list(String userId, String conversationId) {
        return failures.activeInConversation(userId, conversationId).stream().map(row -> {
            var scope = scopeMapper.findOwned(userId, row.getScopeId());
            String executionId = "RESPONSE".equals(scope.getKind()) ? scope.getOwnerId() : null;
            String taskId = "TASK".equals(scope.getKind()) ? scope.getOwnerId() : null;
            String source = failures.sourceMessage(userId, scope.getScopeId());
            int attempt = failures.serviceAttemptsView(userId, scope.getScopeId());
            var next = failures.nextAttempt(userId, scope.getScopeId());
            List<String> actions = executionId != null && failures.retryAllowed(userId, executionId) ? List.of("RETRY") : List.of();
            if ("USER".equals(row.getActor()) && "INPUT_REQUIRED".equals(row.getCategory())
                    && failures.userActionAvailable(userId, row.getBindingId(), "INPUT", LocalDateTime.now(ZoneOffset.UTC))) {
                actions = List.of("PROVIDE_INPUT");
            } else if ("USER".equals(row.getActor()) && "AUTH_REQUIRED".equals(row.getCategory())
                    && failures.userActionAvailable(userId, row.getBindingId(), "APPROVAL", LocalDateTime.now(ZoneOffset.UTC))) {
                actions = List.of("APPROVE");
            }
            String message = "RETRYING".equals(row.getStatus()) ? "回答服务暂时不可用，正在自动恢复（" + attempt + "/2）。" : row.getSafeMessage();
            if ("SERVICE_TRANSIENT".equals(row.getCategory()) && "ACTIVE".equals(row.getStatus()) && attempt >= 2) {
                message = "回答服务仍暂不可用，已暂停重试。你的消息和已完成内容已保留。";
            }
            return new ExecutionFailureView(row.getFailureId(), row.getScopeId(), row.getCategory(), row.getCode(), row.getActor(), message,
                    actions.contains("RETRY"), attempt, 2, row.getDiagnosticId(), actions, executionId,
                    executionId == null ? null : failures.executionVersion(userId, executionId), taskId, source,
                    row.getStatus(), next == null ? null : next.atOffset(ZoneOffset.UTC).toString());
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
        var scope = scopes.lockOwned(target.getUserId(), scopeMapper.findScopeByBinding(target.getUserId(), target.getBindingId()), false);
        var owner = scopeMapper.lockTaskOwner(scope.getUserId(), scope.getOwnerId(), scope.getScopeId());
        if (owner.isCancellationRequested() || !Set.of("RUNNING", "RECOVERY_BLOCKED", "RECONCILING").contains(owner.getStatus())) {
            return;
        }
        String key = receipt.turnId() + ":" + receipt.runId();
        String scopeKey = "task-auto-resume:" + scope.getOwnerId();
        if (commands.findByKeyCurrent(scope.getUserId(), scopeKey, key) != null) {
            return;
        }
        if (!directives.openOperations(scope.getUserId(), scope.getScopeId()).isEmpty()
                || !directives.openInteractions(scope.getUserId(), scope.getScopeId()).isEmpty()) {
            return;
        }
        int attempt = failures.serviceAttempts(scope.getUserId(), scope.getScopeId());
        if (attempt >= 2) {
            return;
        }
        LocalDateTime next = LocalDateTime.now(ZoneOffset.UTC).plusSeconds(1L << (attempt + 1));
        if (failures.reserveServiceRetry(scope.getUserId(), scope.getScopeId(), next) != 1) {
            return;
        }
        register(scope, target.getBindingId(), "interrupted:" + key, new FailureClassification("SERVICE_TRANSIENT", "COORDINATOR_INTERRUPTED", "SYSTEM", "处理曾暂时中断，正在从已保存的位置恢复。"));
        var failure = failures.latest(scope.getUserId(), scope.getScopeId(), target.getBindingId());
        failures.approveRetry(scope.getUserId(), failure.getFailureId(), attempt + 1);
        failures.recoverTask(scope.getUserId(), scope.getOwnerId());
        String payload = RuntimePayloads.toJson(json, Map.of("turnId", receipt.turnId()));
        commands.insertControl(UUID.randomUUID().toString(), scope.getUserId(), scopeKey, key, ContentDigests.sha256(payload), scope.getOwnerId(), target.getBindingId(), "RESUME", payload, next);
        events.publish(scope, "task-resume:" + key, "failure.updated", Map.of("failureId", failure.getFailureId(), "status", "RETRYING", "attempt", attempt + 1));
        log.info("协调中断已按原Turn排队恢复 taskId={} bindingId={} attempt={}", scope.getOwnerId(), target.getBindingId(), attempt + 1);
    }

    public String masterCommand(ProjectionTargetDO target) {
        return failures.latestMasterCommand(target.getUserId(), target.getBindingId());
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
        if (!"TASK".equals(scope.getKind())) {
            return false;
        }
        var failure = failures.latest(scope.getUserId(), scope.getScopeId(), bindingId);
        if (failure == null || "NATIVE_RUN_INCOMPLETE".equals(failure.getCode())
                || "EXTERNAL_UNKNOWN".equals(failure.getCategory()) || "USER".equals(failure.getActor())) {
            return false;
        }
        if (!directives.openOperations(scope.getUserId(), scope.getScopeId()).isEmpty()
                || !directives.openInteractions(scope.getUserId(), scope.getScopeId()).isEmpty()) {
            return false;
        }
        var task = tasks.lockOwnedRow(scope.getUserId(), scope.getOwnerId());
        if (task == null || failures.failTask(scope.getUserId(), scope.getOwnerId()) != 1) {
            return false;
        }
        long barrier = task.getPlanVersion() + 1;
        plans.supersedePlans(task.getTaskId(), barrier);
        plans.supersedeUnstartedJobs(task.getTaskId(), barrier);
        plans.barrierInFlightJobs(task.getTaskId(), barrier);
        plans.cancelSupersededCommands(task.getTaskId(), barrier);
        plans.cancelSupersededSignals(task.getTaskId());
        conversations.clearActiveTask(scope.getUserId(), scope.getConversationId(), task.getTaskId());
        projection.reserveEventSequence(scope.getUserId(), task.getTaskId());
        taskEvents.insertProjected(scope.getUserId(), task.getTaskId(), projection.eventSequence(scope.getUserId(), task.getTaskId()),
                "failure-finalized:" + eventId, "task.completed", RuntimePayloads.toJson(json, Map.of("status", "FAILED", "version", task.getVersion() + 1)), LocalDateTime.now(ZoneOffset.UTC));
        log.info("不可恢复故障已安全终结目标 taskId={} category={} diagnosticId={}", task.getTaskId(), failure.getCategory(), failure.getDiagnosticId());
        return true;
    }

    public boolean canRetry(String userId, String executionId) {
        return failures.retryAllowed(userId, executionId);
    }

    private void register(ExecutionScopeDO scope, String bindingId, String key, FailureClassification classification) {
        String hash = ContentDigests.sha256(key);
        if (failures.byKey(scope.getUserId(), scope.getScopeId(), hash) != null) {
            return;
        }
        // 同一作用域内同一原因只保留一条生效故障：工具校验与模型分类可能先后上报同一次终止。
        if (failures.countActiveByCode(scope.getUserId(), scope.getScopeId(), classification.code()) > 0) {
            return;
        }
        var row = new ExecutionFailureDO(UUID.randomUUID().toString(), scope.getScopeId(), bindingId, classification.category(), classification.code(), classification.actor(), classification.safeMessage(), UUID.randomUUID().toString(), "ACTIVE", 0);
        failures.insert(scope.getUserId(), row, hash);
        if ("RESPONSE".equals(scope.getKind())) {
            failures.linkFailure(scope.getUserId(), bindingId, row.getFailureId());
        }
        events.publish(scope, "failure:" + row.getFailureId(), "failure.updated", Map.of("failureId", row.getFailureId(), "category", row.getCategory(), "actor", row.getActor(), "safeMessage", row.getSafeMessage(), "diagnosticId", row.getDiagnosticId()));
        if (key.startsWith("model:")) {
            // 记录安全错误码、用户可读原因与关联身份，不把供应商原始响应或密钥写入日志。
            log.error("模型调用失败 scopeId={} bindingId={} category={} code={} reason={} diagnosticId={}",
                    scope.getScopeId(), bindingId, row.getCategory(), row.getCode(), row.getSafeMessage(), row.getDiagnosticId());
        } else {
            log.warn("执行故障已记录 scopeId={} bindingId={} category={} code={} reason={} diagnosticId={}",
                    scope.getScopeId(), bindingId, row.getCategory(), row.getCode(), row.getSafeMessage(), row.getDiagnosticId());
        }
    }
}
