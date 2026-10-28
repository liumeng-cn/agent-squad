package com.agentsquad.squad.runtime.service;

import com.agentsquad.squad.common.exception.RuntimeBusinessException;
import com.agentsquad.squad.common.exception.RuntimeExceptionCode;
import com.agentsquad.squad.common.util.RuntimePayloads;
import com.agentsquad.squad.common.util.CanonicalJson;
import com.agentsquad.squad.common.util.ContentDigests;
import com.agentsquad.squad.conversation.repository.BindingMapper;
import com.agentsquad.squad.conversation.repository.CommandMapper;
import com.agentsquad.squad.governance.domain.BudgetAccountRow;
import com.agentsquad.squad.governance.repository.BudgetMapper;
import com.agentsquad.squad.conversation.repository.ExecutionScopeMapper;
import com.agentsquad.squad.conversation.domain.GoalTaskStatus;
import com.agentsquad.squad.conversation.repository.TaskEventMapper;
import com.agentsquad.squad.runtime.domain.PlanContextRow;
import com.agentsquad.squad.runtime.domain.PlanRow;
import com.agentsquad.squad.runtime.domain.ContextSnapshotRow;
import com.agentsquad.squad.runtime.domain.WorkerJobRow;
import com.agentsquad.squad.runtime.dto.PlanNode;
import com.agentsquad.squad.runtime.dto.PlanPublish;
import com.agentsquad.squad.runtime.dto.PlanReceipt;
import com.agentsquad.squad.runtime.repository.PlanMapper;
import com.agentsquad.squad.runtime.repository.ProjectionMapper;
import com.agentsquad.squad.runtime.repository.ContextSnapshotMapper;
import com.agentsquad.squad.worker.repository.WorkerDefinitionMapper;
import com.agentsquad.squad.worker.WorkerDefinitionService;
import com.agentsquad.squad.common.util.JsonSchemaValidator;
import com.agentsquad.squad.worker.dto.WorkerDefinitionResponse;
import com.agentsquad.squad.worker.profile.WorkerModelProfile;
import com.agentsquad.squad.worker.profile.WorkerProfileRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import com.agentsquad.squad.conversation.service.ExecutionScopeService;
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
    private static final Set<String> JOIN_POLICIES = Set.of("ALL_REQUIRED", "BEST_EFFORT");

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
    private JsonSchemaValidator schemaValidator;

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
        PlanContextRow context = planMapper.lockMasterContext(userId, sessionId);
        if (context == null) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.NOT_FOUND, "Master 会话未绑定目标");
        }
        if (context.cancellationRequested() || !"ACTIVE".equals(context.conversationStatus())
                || !(GoalTaskStatus.QUEUED.name().equals(context.taskStatus())
                || GoalTaskStatus.RUNNING.name().equals(context.taskStatus()))) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "目标已不能发布计划");
        }
        PlanRow existing = planMapper.findLatestByLogicalKey(context.taskId(), publish.logicalKey());
        if (existing != null) {
            String requestedDefinition = RuntimePayloads.toJson(objectMapper, publish);
            if (!CanonicalJson.sha256(objectMapper, requestedDefinition)
                    .equals(CanonicalJson.sha256(objectMapper, existing.definitionJson()))) {
                throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "计划逻辑键对应的内容不同");
            }
            List<String> jobIds = planMapper.listJobs(context.taskId(), existing.planVersion())
                    .stream().map(WorkerJobRow::jobId).toList();
            log.info("计划重放返回原回执 taskId={} planId={} planVersion={} jobCount={}",
                    context.taskId(), existing.planId(), existing.planVersion(), jobIds.size());
            return new PlanReceipt(existing.planId(), context.taskId(), existing.planVersion(), jobIds);
        }

        if (publish.expectedPlanVersion() != context.planVersion()) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "计划版本已变化");
        }

        validateDag(publish.nodes());
        validateContexts(context, publish.nodes());
        List<WorkerJobRow> previousJobs = context.planVersion() == 0
                ? List.of() : planMapper.listJobs(context.taskId(), context.planVersion());
        Map<String, WorkerJobRow> reusable = new LinkedHashMap<>();
        boolean foundReusable;
        do {
            foundReusable = false;
            for (PlanNode node : publish.nodes()) {
                if (reusable.containsKey(node.nodeKey())) {
                    continue;
                }
                WorkerJobRow source = reuseService.reusableNode(
                        context.userId(), context.taskId(), node, previousJobs, reusable);
                if (source != null) {
                    reusable.put(node.nodeKey(), source);
                    foundReusable = true;
                }
            }
        } while (foundReusable);
        requirePlanBudget(context, publish.nodes(), reusable.keySet());

        long newVersion = context.planVersion() + 1;
        long priorSignalVersion = 0;
        if (context.planVersion() > 0) {
            PlanRow previous = planMapper.lockPlan(context.userId(), context.taskId(),
                    context.planVersion());
            if (previous == null) {
                throw new IllegalStateException("当前计划头已消失");
            }
            priorSignalVersion = previous.signalVersion();
        }
        String planId = UUID.randomUUID().toString();
        String definitionJson = RuntimePayloads.toJson(objectMapper, publish);
        planMapper.insertPlan(planId, context.userId(), context.taskId(), newVersion,
                publish.logicalKey(), definitionJson, priorSignalVersion);

        List<String> jobIds = new ArrayList<>(publish.nodes().size());
        Map<String, String> reusedJobIds = new LinkedHashMap<>();
        for (PlanNode node : publish.nodes()) {
            String jobId = UUID.randomUUID().toString();
            boolean root = node.dependsOn() == null || node.dependsOn().isEmpty();
            String status = root ? "READY" : "BLOCKED";
            String dependsJson = root ? null : RuntimePayloads.toJson(objectMapper, node.dependsOn());
            Map<String, String> input = new LinkedHashMap<>();
            input.put("goal", node.goal());
            input.put("contextSnapshotId", node.contextSnapshotId());
            planMapper.insertJob(jobId, context.userId(), context.taskId(), newVersion,
                    node.nodeKey(), node.workerKey(), node.workerVersion(), status,
                    dependsJson, RuntimePayloads.toJson(objectMapper, input));
            WorkerJobRow source = reusable.get(node.nodeKey());
            if (source != null) {
                if (planMapper.reuseResult(context.userId(), jobId,
                        source.jobId(), source.resultJson()) != 1) {
                    throw new IllegalStateException("复用节点结果写入失败");
                }
                reusedJobIds.put(jobId, source.jobId());
            } else if (root) {
                createRootDelivery(context, node, jobId, newVersion);
            }
            jobIds.add(jobId);
        }

        if (planMapper.advanceTaskPlan(context.userId(), context.taskId(),
                publish.expectedPlanVersion(), newVersion) != 1) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "计划发布时目标状态已变化");
        }

        planMapper.supersedePlans(context.taskId(), newVersion);
        planMapper.supersedeUnstartedJobs(context.taskId(), newVersion);
        planMapper.barrierInFlightJobs(context.taskId(), newVersion);
        planMapper.cancelSupersededCommands(context.taskId(), newVersion);
        planMapper.cancelSupersededSignals(context.taskId());
        if (!reusedJobIds.isEmpty()) {
            dependencyService.activateReadyDependents(context.userId(), context.taskId(), newVersion);
            PlanRow activePlan = planMapper.lockPlan(context.userId(), context.taskId(), newVersion);
            for (String ignored : reusedJobIds.keySet()) {
                masterSignalService.onJobStateChanged(activePlan);
            }
        }

        Map<String, Object> eventPayload = new LinkedHashMap<>();
        eventPayload.put("planVersion", newVersion);
        eventPayload.put("jobIds", jobIds);
        if (projectionMapper.reserveEventSequence(context.userId(), context.taskId()) != 1) {
            throw new IllegalStateException("计划事件序号预留失败");
        }
        taskEventMapper.insertProjected(context.userId(), context.taskId(),
                projectionMapper.eventSequence(context.userId(), context.taskId()),
                "plan:" + planId, "plan.published", RuntimePayloads.toJson(objectMapper, eventPayload),
                LocalDateTime.now());
        for (Map.Entry<String, String> reuse : reusedJobIds.entrySet()) {
            if (projectionMapper.reserveEventSequence(context.userId(), context.taskId()) != 1) {
                throw new IllegalStateException("结果复用事件序号预留失败");
            }
            taskEventMapper.insertProjected(context.userId(), context.taskId(),
                    projectionMapper.eventSequence(context.userId(), context.taskId()),
                    "job:reused:" + reuse.getKey(), "job.reused",
                    RuntimePayloads.toJson(objectMapper, Map.of(
                            "jobId", reuse.getKey(), "reusedFromJobId", reuse.getValue(),
                            "planVersion", newVersion)), LocalDateTime.now());
        }

        log.info("计划已发布 taskId={} planVersion={} planId={} jobCount={}",
                context.taskId(), newVersion, planId, jobIds.size());
        return new PlanReceipt(planId, context.taskId(), newVersion, jobIds);
    }

    /**
     * 发布前按每个节点的一次最大模型调用估算最低启动额；余额不足时整个发布事务回滚。
     */
    private void requirePlanBudget(PlanContextRow context, List<PlanNode> nodes,
                                   Set<String> reusedNodeKeys) {
        String scopeId = scopeMapper.findScopeByTask(context.userId(), context.taskId());
        BudgetAccountRow taskBudget = budgetMapper.lockAccount(context.userId(), scopeId);
        if (taskBudget == null) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.NOT_FOUND, "目标预算不存在");
        }
        BigInteger required = BigInteger.valueOf(budgetMapper.sumConsumedUnits(
                context.userId(), scopeId));
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
        if (required.compareTo(BigInteger.valueOf(taskBudget.limitMicrounits())) > 0) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT,
                    "目标预算不足以启动计划节点");
        }
    }

    /**
     * 根节点与固定 Binding/Command 同事务落库；原生提交失败时由同一命令键重投。
     */
    private void createRootDelivery(PlanContextRow context, PlanNode node,
                                    String jobId, long planVersion) {
        WorkerDefinitionResponse definition = workerDefinitionService.requireEnabled(
                node.workerKey(), node.workerVersion());
        String bindingId = UUID.randomUUID().toString();
        String agentId = ContentDigests.sha256(definition.definitionId()).substring(0, 32);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        bindingMapper.insertWorker(bindingId, context.userId(), context.taskId(), agentId,
                UUID.randomUUID().toString(), now);
        if (planMapper.bindReadyJob(context.userId(), jobId, bindingId) != 1) {
            throw new IllegalStateException("根节点绑定失败");
        }
        String commandId = UUID.randomUUID().toString();
        String payload = RuntimePayloads.toJson(objectMapper, Map.of("message", node.goal()));
        commandMapper.insertSubmit(commandId, context.userId(), "worker-job:" + jobId,
                "plan:" + planVersion + ":" + node.nodeKey(), ContentDigests.sha256(payload),
                context.taskId(), bindingId, payload, now);
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
        if (!JOIN_POLICIES.contains(publish.joinPolicy())) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION, "汇合策略无效");
        }
        if (publish.acceptanceCriteria() == null || publish.acceptanceCriteria().isEmpty()) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION, "缺少完成条件");
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
    private void validateContexts(PlanContextRow context, List<PlanNode> nodes) {
        for (PlanNode node : nodes) {
            ContextSnapshotRow snapshot = contextSnapshotMapper.findOwned(
                    context.userId(), context.taskId(), node.contextSnapshotId());
            if (snapshot == null || !node.nodeKey().equals(snapshot.targetNodeKey())
                    || snapshot.conversationGeneration() != context.conversationGeneration()
                    || snapshot.memoryRevision() != 0
                    || !CanonicalJson.sha256(objectMapper, snapshot.itemsJson()).equals(snapshot.contentHash())) {
                throw new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION,
                        "计划节点的局部上下文未准备或已失效");
            }
            WorkerDefinitionResponse definition = workerDefinitionService.requireEnabled(
                    node.workerKey(), node.workerVersion());
            schemaValidator.requireMatches(objectMapper.valueToTree(Map.of(
                    "goal", node.goal(), "contextSnapshotId", node.contextSnapshotId())),
                    definition.inputSchema(), "Worker 输入");
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
