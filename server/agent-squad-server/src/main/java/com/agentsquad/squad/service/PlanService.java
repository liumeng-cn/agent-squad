package com.agentsquad.squad.service;

import com.agentsquad.squad.mapper.TaskDirectiveMapper;
import com.agentsquad.squad.common.exception.RuntimeBusinessException;
import com.agentsquad.squad.domain.enums.RuntimeExceptionCode;
import com.agentsquad.squad.common.util.RuntimePayloads;
import com.agentsquad.squad.common.util.CanonicalJson;
import com.agentsquad.squad.common.util.ContentDigests;
import com.agentsquad.squad.mapper.BindingMapper;
import com.agentsquad.squad.mapper.CommandMapper;
import com.agentsquad.squad.domain.entity.BudgetAccountDO;
import com.agentsquad.squad.common.exception.PendingDirectivesException;
import com.agentsquad.squad.mapper.BudgetMapper;
import com.agentsquad.squad.mapper.ExecutionScopeMapper;
import com.agentsquad.squad.domain.enums.GoalTaskStatus;
import com.agentsquad.squad.mapper.TaskEventMapper;
import com.agentsquad.squad.domain.entity.PlanContextDO;
import com.agentsquad.squad.domain.entity.PlanDO;
import com.agentsquad.squad.domain.entity.ContextSnapshotDO;
import com.agentsquad.squad.domain.entity.WorkerJobDO;
import com.agentsquad.squad.domain.dto.PlanNode;
import com.agentsquad.squad.domain.dto.PlanPublish;
import com.agentsquad.squad.domain.dto.PlanReceipt;
import com.agentsquad.squad.common.model.PendingDirectiveFeedback;
import com.agentsquad.squad.mapper.PlanMapper;
import com.agentsquad.squad.mapper.ProjectionMapper;
import com.agentsquad.squad.mapper.ContextSnapshotMapper;
import com.agentsquad.squad.mapper.WorkerDefinitionMapper;
import com.agentsquad.squad.domain.dto.WorkerDefinitionResponse;
import com.agentsquad.squad.worker.profile.WorkerModelProfile;
import com.agentsquad.squad.worker.profile.WorkerProfileRegistry;
import com.agentsquad.squad.mapper.TaskMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;

/**
 * Master 计划发布服务。
 * <p>
 * publish 校验 DAG 后在一个平台事务内创建不可变 Plan、全部 Job 与 plan.published 事件；
 * 根节点 READY、其余 BLOCKED，并推进目标 planVersion。同一 logicalKey 重放返回原回执，不重复建节点。
 */
@Service
@Slf4j
public class PlanService {

    private static final int MAX_NODES = 16;
    private static final int MAX_PARALLEL = 4;

    @Resource
    private ExecutionAuthorizationService authorization;

    @Resource
    private TaskEventPublishingService conversationProjection;

    @Resource
    private TaskDirectiveMapper directives;

    @Resource
    private TaskMapper tasks;

    @Resource
    private ExecutionScopeService executionScopes;

    @Resource
    private PlanMapper planMapper;

    @Resource
    private ContextSnapshotMapper contextSnapshotMapper;

    @Resource
    private WorkerDefinitionMapper workerDefinitionMapper;

    @Resource
    private WorkerDefinitionService workerDefinitionService;

    @Resource
    private WorkerResultReuseService reuseService;

    @Resource
    private WorkerDependencyService dependencyService;

    @Resource
    private MasterSignalService masterSignalService;

    @Resource
    private WorkerProfileRegistry profileRegistry;

    @Resource
    private ExecutionScopeMapper scopeMapper;

    @Resource
    private BudgetMapper budgetMapper;

    @Resource
    private BindingMapper bindingMapper;

    @Resource
    private CommandMapper commandMapper;

    @Resource
    private TaskEventMapper taskEventMapper;

    @Resource
    private ProjectionMapper projectionMapper;

    @Resource
    private ObjectMapper objectMapper;

    /**
     * 发布计划。用户与绑定来自受控 Master 会话，任务归属由数据库锁定解析；模型不能指定目标 ID。
     * 事务提交后即使工具回包丢失，同 logicalKey 重放也只返回原回执。
     */
    @Transactional
    public PlanReceipt publish(String userId, String sessionId, PlanPublish publish) {
        validateShape(publish);

        executionScopes.lockOwned(userId, executionScopes.scopeForSession(userId, sessionId), true);
        PlanContextDO context = planMapper.lockMasterContext(userId, sessionId);
        if (context == null) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.NOT_FOUND, "Master 会话未绑定目标");
        }
        if (context.isCancellationRequested() || !"ACTIVE".equals(context.getConversationStatus())
                || !(GoalTaskStatus.QUEUED.name().equals(context.getTaskStatus())
                || GoalTaskStatus.RUNNING.name().equals(context.getTaskStatus()))) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "目标已不能发布计划");
        }
        long goalRevision = directives.goalState(userId, context.getTaskId()).getGoalRevision();
        List<String> pendingDirectives = directives.pending(userId, context.getTaskId());
        if (!pendingDirectives.isEmpty()) {
            // 在已锁定的目标范围内返回全部真实标识，避免模型猜测来源消息 ID。
            long expectedVersion = tasks.lockOwnedRow(userId, context.getTaskId()).getVersion();
            List<PendingDirectiveFeedback> pending = new ArrayList<>();
            for (String directiveId : pendingDirectives) {
                var directive = directives.lock(userId, directiveId);
                if (directive != null && context.getTaskId().equals(directive.getTaskId())
                        && "ACCEPTED".equals(directive.getStatus())) {
                    pending.add(new PendingDirectiveFeedback(
                            directive.getDirectiveId(), expectedVersion, directive.getKind(), directive.getContent()));
                }
            }
            throw new PendingDirectivesException(context.getTaskId(), pending);
        }
        PlanDO existing = planMapper.findLatestByLogicalKey(context.getTaskId(), publish.logicalKey());
        if (existing != null) {
            if (existing.getGoalRevision() != goalRevision) {
                throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "旧计划对应的目标内容已变化，请使用新的计划键");
            }
            String requestedDefinition = RuntimePayloads.toJson(objectMapper, publish);
            if (!CanonicalJson.sha256(objectMapper, requestedDefinition)
                    .equals(CanonicalJson.sha256(objectMapper, existing.getDefinitionJson()))) {
                throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "计划逻辑键对应的内容不同");
            }
            List<String> jobIds = planMapper.listJobs(context.getTaskId(), existing.getPlanVersion())
                    .stream().map(WorkerJobDO::getJobId).toList();
            log.info("计划重放返回原回执 taskId={} planId={} planVersion={} jobCount={}",
                    context.getTaskId(), existing.getPlanId(), existing.getPlanVersion(), jobIds.size());
            return new PlanReceipt(existing.getPlanId(), context.getTaskId(), existing.getPlanVersion(), jobIds);
        }

        if (publish.expectedPlanVersion() != context.getPlanVersion()) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "计划版本已变化");
        }

        validateDag(publish.nodes());
        validateContexts(context, publish.nodes());
        List<WorkerJobDO> previousJobs = context.getPlanVersion() == 0
                ? List.of() : planMapper.listJobs(context.getTaskId(), context.getPlanVersion());
        Map<String, WorkerJobDO> reusable = new LinkedHashMap<>();
        boolean foundReusable;
        do {
            foundReusable = false;
            for (PlanNode node : publish.nodes()) {
                if (reusable.containsKey(node.nodeKey())) {
                    continue;
                }
                WorkerJobDO source = reuseService.reusableNode(
                        context.getUserId(), context.getTaskId(), node, previousJobs, reusable);
                if (source != null) {
                    reusable.put(node.nodeKey(), source);
                    foundReusable = true;
                }
            }
        } while (foundReusable);
        requirePlanBudget(context, publish.nodes(), reusable.keySet());

        long newVersion = context.getPlanVersion() + 1;
        long priorSignalVersion = 0;
        if (context.getPlanVersion() > 0) {
            PlanDO previous = planMapper.lockPlan(context.getUserId(), context.getTaskId(),
                    context.getPlanVersion());
            if (previous == null) {
                throw new IllegalStateException("当前计划头已消失");
            }
            priorSignalVersion = previous.getSignalVersion();
        }
        String planId = UUID.randomUUID().toString();
        String definitionJson = RuntimePayloads.toJson(objectMapper, publish);
        planMapper.insertPlan(planId, context.getUserId(), context.getTaskId(), newVersion,
                publish.logicalKey(), definitionJson, priorSignalVersion, goalRevision);

        List<String> jobIds = new ArrayList<>(publish.nodes().size());
        Map<String, String> reusedJobIds = new LinkedHashMap<>();
        for (PlanNode node : publish.nodes()) {
            String jobId = UUID.randomUUID().toString();
            boolean root = node.dependsOn() == null || node.dependsOn().isEmpty();
            String status = root ? "READY" : "BLOCKED";
            String dependsJson = root ? null : RuntimePayloads.toJson(objectMapper, node.dependsOn());
            Map<String, Object> input = new LinkedHashMap<>();
            input.put("goal", node.goal());
            input.put("contextSnapshotId", node.contextSnapshotId());
            input.put("required", node.required());
            planMapper.insertJob(jobId, context.getUserId(), context.getTaskId(), newVersion,
                    node.nodeKey(), node.workerKey(), node.workerVersion(), status,
                    dependsJson, RuntimePayloads.toJson(objectMapper, input));
            WorkerJobDO source = reusable.get(node.nodeKey());
            if (source != null) {
                if (planMapper.reuseResult(context.getUserId(), jobId,
                        source.getJobId(), source.getResultJson()) != 1) {
                    throw new IllegalStateException("复用节点结果写入失败");
                }
                reusedJobIds.put(jobId, source.getJobId());
            } else if (root) {
                createRootDelivery(context, node, jobId, newVersion);
            }
            jobIds.add(jobId);
        }

        if (planMapper.advanceTaskPlan(context.getUserId(), context.getTaskId(),
                publish.expectedPlanVersion(), newVersion) != 1) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "计划发布时目标状态已变化");
        }

        planMapper.supersedePlans(context.getTaskId(), newVersion);
        planMapper.supersedeUnstartedJobs(context.getTaskId(), newVersion);
        planMapper.barrierInFlightJobs(context.getTaskId(), newVersion);
        planMapper.cancelSupersededCommands(context.getTaskId(), newVersion);
        planMapper.cancelSupersededSignals(context.getTaskId());
        if (!reusedJobIds.isEmpty()) {
            dependencyService.activateReadyDependents(context.getUserId(), context.getTaskId(), newVersion);
            PlanDO activePlan = planMapper.lockPlan(context.getUserId(), context.getTaskId(), newVersion);
            for (String ignored : reusedJobIds.keySet()) {
                masterSignalService.onJobStateChanged(activePlan);
            }
        }

        Map<String, Object> eventPayload = new LinkedHashMap<>();
        eventPayload.put("planVersion", newVersion);
        eventPayload.put("jobIds", jobIds);
        eventPayload.put("joinPolicy", publish.joinPolicy());
        eventPayload.put("acceptanceCriteria", publish.acceptanceCriteria());
        if (projectionMapper.reserveEventSequence(context.getUserId(), context.getTaskId()) != 1) {
            throw new IllegalStateException("计划事件序号预留失败");
        }
        conversationProjection.insertProjected(context.getUserId(), context.getTaskId(),
                projectionMapper.eventSequence(context.getUserId(), context.getTaskId()),
                "plan:" + planId, "plan.published", RuntimePayloads.toJson(objectMapper, eventPayload),
                LocalDateTime.now());
        for (Map.Entry<String, String> reuse : reusedJobIds.entrySet()) {
            if (projectionMapper.reserveEventSequence(context.getUserId(), context.getTaskId()) != 1) {
                throw new IllegalStateException("结果复用事件序号预留失败");
            }
            conversationProjection.insertProjected(context.getUserId(), context.getTaskId(),
                    projectionMapper.eventSequence(context.getUserId(), context.getTaskId()),
                    "job:reused:" + reuse.getKey(), "job.reused",
                    RuntimePayloads.toJson(objectMapper, Map.of(
                            "jobId", reuse.getKey(), "reusedFromJobId", reuse.getValue(),
                            "planVersion", newVersion)), LocalDateTime.now());
        }

        log.info("计划已发布 taskId={} planVersion={} planId={} jobCount={}",
                context.getTaskId(), newVersion, planId, jobIds.size());
        return new PlanReceipt(planId, context.getTaskId(), newVersion, jobIds);
    }

    /**
     * 发布前按每个节点的一次最大模型调用估算最低启动额；余额不足时整个发布事务回滚。
     */
    private void requirePlanBudget(PlanContextDO context, List<PlanNode> nodes,
                                   Set<String> reusedNodeKeys) {
        String scopeId = scopeMapper.findScopeByTask(context.getUserId(), context.getTaskId());
        BudgetAccountDO taskBudget = budgetMapper.lockAccount(context.getUserId(), scopeId);
        if (taskBudget == null) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.NOT_FOUND, "目标预算不存在");
        }
        BigInteger required = BigInteger.valueOf(budgetMapper.sumConsumedUnits(
                context.getUserId(), scopeId));
        for (PlanNode node : nodes) {
            if (reusedNodeKeys.contains(node.nodeKey())) {
                continue;
            }
            WorkerDefinitionResponse definition = workerDefinitionService.requireEnabled(
                    node.workerKey(), node.workerVersion());
            WorkerModelProfile profile = profileRegistry.requirePricedModel(definition.modelProfileKey());
            BigInteger input = BigInteger.valueOf(profile.getMaxInputTokens())
                    .multiply(BigInteger.valueOf(profile.getInputMicrounitsPerMillionTokens()));
            BigInteger output = BigInteger.valueOf(profile.getMaxOutputTokens())
                    .multiply(BigInteger.valueOf(profile.getOutputMicrounitsPerMillionTokens()));
            BigInteger cost = input.add(output).add(BigInteger.valueOf(999999))
                    .divide(BigInteger.valueOf(1000000));
            required = required.add(cost.max(BigInteger.ONE));
        }
        if (required.compareTo(BigInteger.valueOf(taskBudget.getLimitMicrounits())) > 0) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT,
                    "目标预算不足以启动计划节点");
        }
    }

    /**
     * 根节点与固定 Binding/Command 同事务落库；原生提交失败时由同一命令键重投。
     */
    private void createRootDelivery(PlanContextDO context, PlanNode node,
                                    String jobId, long planVersion) {
        WorkerDefinitionResponse definition = workerDefinitionService.requireEnabled(
                node.workerKey(), node.workerVersion());
        String bindingId = UUID.randomUUID().toString();
        String agentId = ContentDigests.sha256(definition.definitionId()).substring(0, 32);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        bindingMapper.insertWorker(bindingId, context.getUserId(), context.getTaskId(), agentId,
                UUID.randomUUID().toString(), now);
        if (planMapper.bindReadyJob(context.getUserId(), jobId, bindingId) != 1) {
            throw new IllegalStateException("根节点绑定失败");
        }
        String commandId = UUID.randomUUID().toString();
        String payload = RuntimePayloads.toJson(objectMapper, Map.of("message", node.goal()));
        commandMapper.insertSubmit(commandId, context.getUserId(), "worker-job:" + jobId,
                "plan:" + planVersion + ":" + node.nodeKey(), ContentDigests.sha256(payload),
                context.getTaskId(), bindingId, payload, now);
    }

    private void validateShape(PlanPublish publish) {
        if (publish == null || isBlank(publish.logicalKey()) || publish.logicalKey().length() > 160) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION, "计划逻辑键无效");
        }
        if (publish.expectedPlanVersion() < 0) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION, "计划版本无效");
        }
        if (publish.nodes() == null || publish.nodes().isEmpty() || publish.nodes().size() > MAX_NODES) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION, "节点数量超出范围");
        }
        if (publish.joinPolicy() == null) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION, "汇合策略无效");
        }
        if (publish.acceptanceCriteria() == null || publish.acceptanceCriteria().isEmpty()) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION, "缺少完成条件");
        }
        if (publish.nodes().stream().noneMatch(PlanNode::required)
                || publish.acceptanceCriteria().stream().noneMatch(item -> item != null && item.required())) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION, "计划至少需要一项必要工作和必要完成条件");
        }
        var criterionIds = new HashSet<String>();
        if (publish.acceptanceCriteria().size() > 20) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION, "完成条件不能超过20项");
        }
        for (var criterion : publish.acceptanceCriteria()) {
            if (criterion == null || isBlank(criterion.criterionId()) || criterion.criterionId().length() > 80
                    || isBlank(criterion.text()) || criterion.text().length() > 2000
                    || criterion.verificationType() == null || !criterionIds.add(criterion.criterionId())
                    || (criterion.deterministicCheck() != null
                    && criterion.verificationType() != com.agentsquad.squad.domain.enums.AcceptanceVerificationType.DETERMINISTIC)) {
                throw new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION, "完成条件内容或标识无效");
            }
        }
        for (PlanNode node : publish.nodes()) {
            if (isBlank(node.nodeKey()) || node.nodeKey().length() > 80
                    || isBlank(node.workerKey()) || isBlank(node.workerVersion())
                    || isBlank(node.goal()) || isBlank(node.contextSnapshotId())
                    || node.maxRecoveryAttempts() < 0 || node.maxRecoveryAttempts() > 3) {
                throw new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION, "计划节点内容无效");
            }
        }
    }

    /**
     * 节点只引用同用户、同目标、同对话代际且内容摘要未变化的已准备快照。
     */
    private void validateContexts(PlanContextDO context, List<PlanNode> nodes) {
        for (PlanNode node : nodes) {
            ContextSnapshotDO snapshot = contextSnapshotMapper.findOwned(
                    context.getUserId(), context.getTaskId(), node.contextSnapshotId());
            if (snapshot == null || !node.nodeKey().equals(snapshot.getTargetNodeKey())
                    || snapshot.getConversationGeneration() != context.getConversationGeneration()
                    || snapshot.getGoalRevision() != directives.goalState(context.getUserId(), context.getTaskId()).getGoalRevision()
                    || !CanonicalJson.sha256(objectMapper, snapshot.getItemsJson()).equals(snapshot.getContentHash())) {
                throw new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION,
                        "计划节点的局部上下文未准备或已失效");
            }
            authorization.requireScopeCurrent(context.getUserId(), executionScopes.scopeForTask(context.getUserId(), context.getTaskId()));
        }
    }

    /**
     * 校验节点键唯一、依赖引用存在、能力版本可用，并用拓扑分层检查无环与并行宽度上限。
     */
    private void validateDag(List<PlanNode> nodes) {
        Map<String, Set<String>> dependents = new HashMap<>();
        Map<String, Integer> indegree = new HashMap<>();
        Set<String> keys = new HashSet<>();
        for (PlanNode node : nodes) {
            if (!keys.add(node.nodeKey())) {
                throw new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION, "计划节点键重复");
            }
            if (workerDefinitionMapper.countEnabled(node.workerKey(), node.workerVersion()) == 0) {
                throw new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION, "Worker 能力版本不可用");
            }
        }
        for (PlanNode node : nodes) {
            Set<String> deps = node.dependsOn() == null ? Set.of() : new HashSet<>(node.dependsOn());
            for (String dep : deps) {
                if (!keys.contains(dep)) {
                    throw new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION, "依赖节点不存在");
                }
                dependents.computeIfAbsent(dep, ignored -> new HashSet<>()).add(node.nodeKey());
            }
            indegree.put(node.nodeKey(), deps.size());
        }

        Queue<String> frontier = new ArrayDeque<>();
        for (Map.Entry<String, Integer> entry : indegree.entrySet()) {
            if (entry.getValue() == 0) {
                frontier.add(entry.getKey());
            }
        }
        int processed = 0;
        int maxParallel = 0;
        while (!frontier.isEmpty()) {
            maxParallel = Math.max(maxParallel, frontier.size());
            List<String> batch = new ArrayList<>(frontier);
            frontier.clear();
            for (String key : batch) {
                processed++;
                for (String dependent : dependents.getOrDefault(key, Set.of())) {
                    int remaining = indegree.merge(dependent, -1, Integer::sum);
                    if (remaining == 0) {
                        frontier.add(dependent);
                    }
                }
            }
        }
        if (processed != nodes.size()) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION, "计划存在循环依赖");
        }
        if (maxParallel > MAX_PARALLEL) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION, "计划并行度超过上限");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
