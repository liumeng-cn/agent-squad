package com.agentsquad.squad.service;

import com.agentsquad.squad.common.util.ContentDigests;
import com.agentsquad.squad.domain.enums.WorkerJobStatus;
import com.agentsquad.squad.domain.enums.WorkerSkipReason;
import com.agentsquad.squad.domain.model.WorkerDependencyNode;
import com.agentsquad.squad.domain.model.WorkerDependencyPolicy;
import com.agentsquad.squad.common.util.RuntimePayloads;
import com.agentsquad.squad.mapper.BindingMapper;
import com.agentsquad.squad.mapper.CommandMapper;
import com.agentsquad.squad.domain.entity.WorkerJobDO;
import com.agentsquad.squad.mapper.PlanMapper;
import com.agentsquad.squad.mapper.ProjectionMapper;
import com.agentsquad.squad.domain.dto.WorkerDefinitionResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Worker 依赖节点激活服务。
 * <p>
 * 在同一事务内检查当前计划的依赖结果：前置成功则创建执行绑定和命令，
 * 前置已明确失败则递归跳过未执行的下游，并保存用户可见原因。
 */
@Service
@Slf4j
public class WorkerDependencyService {

    @Resource
    private PlanMapper planMapper;

    @Resource
    private BindingMapper bindingMapper;

    @Resource
    private CommandMapper commandMapper;

    @Resource
    private WorkerDefinitionService definitionService;

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private ProjectionMapper projections;

    @Resource
    private TaskEventPublishingService events;

    /**
     * 调用方已锁定父 Task 和 Plan；依赖读取使用当前读，避免并行分支相互看不到已提交结果。
     */
    public void activateReadyDependents(String userId, String taskId, long planVersion) {
        List<WorkerJobDO> jobs = planMapper.lockJobs(taskId, planVersion);
        var nodes = jobs.stream().map(job -> new WorkerDependencyNode(job.getNodeKey(),
                WorkerJobStatus.valueOf(job.getStatus()), dependencyNames(job))).toList();
        var skipped = WorkerDependencyPolicy.failedDependents(nodes);
        for (var job : jobs) {
            if (skipped.contains(job.getNodeKey())) {
                skip(job);
            }
        }
        Map<String, WorkerJobDO> byNodeKey = new HashMap<>();
        for (WorkerJobDO job : jobs) {
            byNodeKey.put(job.getNodeKey(), job);
        }
        for (WorkerJobDO job : jobs) {
            if (!WorkerJobStatus.BLOCKED.name().equals(job.getStatus()) || skipped.contains(job.getNodeKey())) {
                continue;
            }
            List<WorkerJobDO> dependencies = completedDependencies(job, byNodeKey);
            if (dependencies != null) {
                activate(job, dependencies);
            }
        }
    }

    private List<String> dependencyNames(WorkerJobDO job) {
        if (job.getDependsOnJson() == null) {
            return List.of();
        }
        JsonNode names = readJson(job.getDependsOnJson());
        if (names == null || names.isNull()) {
            return List.of();
        }
        if (!names.isArray()) {
            throw new IllegalStateException("工作依赖格式无效");
        }
        var result = new ArrayList<String>();
        names.forEach(name -> result.add(name.asText()));
        return result;
    }

    /** 调用方持有当前Task/Plan锁；跳过和公开原因一起提交，重复处理不会重复生成事件。 */
    private void skip(WorkerJobDO job) {
        if (planMapper.skipBlockedJob(job.getUserId(), job.getTaskId(), job.getPlanVersion(), job.getJobId()) != 1) {
            throw new IllegalStateException("依赖失败收尾时计划或节点状态已变化");
        }
        if (projections.reserveEventSequence(job.getUserId(), job.getTaskId()) != 1) {
            throw new IllegalStateException("工作跳过事件序号预留失败");
        }
        events.insertProjected(job.getUserId(), job.getTaskId(), projections.eventSequence(job.getUserId(), job.getTaskId()),
                "dependency-skipped:" + job.getJobId(), "job.skipped",
                RuntimePayloads.toJson(objectMapper, Map.of("jobId", job.getJobId(), "planVersion", job.getPlanVersion(),
                        "status", WorkerJobStatus.SKIPPED, "reason", WorkerSkipReason.DEPENDENCY_FAILED)),
                LocalDateTime.now(ZoneOffset.UTC));
        log.info("前置工作失败，未执行后续工作 taskId={} jobId={} planVersion={} reason={}",
                job.getTaskId(), job.getJobId(), job.getPlanVersion(), WorkerSkipReason.DEPENDENCY_FAILED);
    }

    private List<WorkerJobDO> completedDependencies(WorkerJobDO job,
                                                     Map<String, WorkerJobDO> byNodeKey) {
        JsonNode names = readJson(job.getDependsOnJson());
        if (names == null || !names.isArray() || names.isEmpty()) {
            throw new IllegalStateException("BLOCKED Job 缺少有效依赖");
        }
        List<WorkerJobDO> dependencies = new ArrayList<>();
        for (JsonNode name : names) {
            WorkerJobDO dependency = byNodeKey.get(name.asText());
            if (dependency == null) {
                throw new IllegalStateException("计划依赖节点已消失");
            }
            if (!"SUCCEEDED".equals(dependency.getStatus())) {
                return null;
            }
            dependencies.add(dependency);
        }
        return dependencies;
    }

    private void activate(WorkerJobDO job, List<WorkerJobDO> dependencies) {
        WorkerDefinitionResponse definition = definitionService.requireEnabled(
                job.getWorkerKey(), job.getWorkerVersion());
        String bindingId = UUID.randomUUID().toString();
        if (planMapper.activateBlockedJob(job.getUserId(), job.getJobId(), bindingId) != 1) {
            throw new IllegalStateException("依赖节点激活竞争");
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        String agentId = ContentDigests.sha256(definition.definitionId()).substring(0, 32);
        bindingMapper.insertWorker(bindingId, job.getUserId(), job.getTaskId(), agentId,
                UUID.randomUUID().toString(), now);

        JsonNode input = readJson(job.getInputJson());
        List<Map<String, Object>> results = new ArrayList<>();
        for (WorkerJobDO dependency : dependencies) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("jobId", dependency.getJobId());
            result.put("nodeKey", dependency.getNodeKey());
            result.put("result", readJson(dependency.getResultJson()));
            results.add(result);
        }
        String message = input.path("goal").asText() + "\n已完成依赖节点结果："
                + RuntimePayloads.toJson(objectMapper, results);
        String payload = RuntimePayloads.toJson(objectMapper, Map.of("message", message));
        String commandId = UUID.randomUUID().toString();
        commandMapper.insertSubmit(commandId, job.getUserId(), "worker-job:" + job.getJobId(),
                "plan:" + job.getPlanVersion() + ":" + job.getNodeKey(), ContentDigests.sha256(payload),
                job.getTaskId(), bindingId, payload, now);
        log.info("依赖 Worker 已激活 taskId={} jobId={} bindingId={} commandId={} dependencyCount={}",
                job.getTaskId(), job.getJobId(), bindingId, commandId, dependencies.size());
    }

    private JsonNode readJson(String value) {
        try {
            return RuntimePayloads.readJson(objectMapper, value);
        } catch (Exception exception) {
            throw new IllegalStateException("计划节点固定数据无法读取", exception);
        }
    }
}
