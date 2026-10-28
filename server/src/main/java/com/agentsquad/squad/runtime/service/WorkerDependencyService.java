package com.agentsquad.squad.runtime.service;

import com.agentsquad.squad.common.util.ContentDigests;
import com.agentsquad.squad.common.util.RuntimePayloads;
import com.agentsquad.squad.conversation.repository.BindingMapper;
import com.agentsquad.squad.conversation.repository.CommandMapper;
import com.agentsquad.squad.runtime.domain.WorkerJobRow;
import com.agentsquad.squad.runtime.repository.PlanMapper;
import com.agentsquad.squad.worker.WorkerDefinitionService;
import com.agentsquad.squad.worker.dto.WorkerDefinitionResponse;
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
 * 原生成功事实写入 Job 后，在同一事务内检查固定计划的依赖结果，
 * 将满足条件的 BLOCKED 节点与 Binding、SUBMIT 命令一起持久化。
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

    /**
     * 调用方已锁定父 Task 和 Plan；依赖读取使用当前读，避免并行分支相互看不到已提交结果。
     */
    public void activateReadyDependents(String userId, String taskId, long planVersion) {
        List<WorkerJobRow> jobs = planMapper.lockJobs(taskId, planVersion);
        Map<String, WorkerJobRow> byNodeKey = new HashMap<>();
        for (WorkerJobRow job : jobs) {
            byNodeKey.put(job.nodeKey(), job);
        }
        for (WorkerJobRow job : jobs) {
            if (!"BLOCKED".equals(job.status())) {
                continue;
            }
            List<WorkerJobRow> dependencies = completedDependencies(job, byNodeKey);
            if (dependencies != null) {
                activate(job, dependencies);
            }
        }
    }

    private List<WorkerJobRow> completedDependencies(WorkerJobRow job,
                                                     Map<String, WorkerJobRow> byNodeKey) {
        JsonNode names = readJson(job.dependsOnJson());
        if (names == null || !names.isArray() || names.isEmpty()) {
            throw new IllegalStateException("BLOCKED Job 缺少有效依赖");
        }
        List<WorkerJobRow> dependencies = new ArrayList<>();
        for (JsonNode name : names) {
            WorkerJobRow dependency = byNodeKey.get(name.asText());
            if (dependency == null) {
                throw new IllegalStateException("计划依赖节点已消失");
            }
            if (!"SUCCEEDED".equals(dependency.status())) {
                return null;
            }
            dependencies.add(dependency);
        }
        return dependencies;
    }

    private void activate(WorkerJobRow job, List<WorkerJobRow> dependencies) {
        WorkerDefinitionResponse definition = definitionService.requireEnabled(
                job.workerKey(), job.workerVersion());
        String bindingId = UUID.randomUUID().toString();
        if (planMapper.activateBlockedJob(job.userId(), job.jobId(), bindingId) != 1) {
            throw new IllegalStateException("依赖节点激活竞争");
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        String agentId = ContentDigests.sha256(definition.definitionId()).substring(0, 32);
        bindingMapper.insertWorker(bindingId, job.userId(), job.taskId(), agentId,
                UUID.randomUUID().toString(), now);

        JsonNode input = readJson(job.inputJson());
        List<Map<String, Object>> results = new ArrayList<>();
        for (WorkerJobRow dependency : dependencies) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("jobId", dependency.jobId());
            result.put("nodeKey", dependency.nodeKey());
            result.put("result", readJson(dependency.resultJson()));
            results.add(result);
        }
        String message = input.path("goal").asText() + "\n已完成依赖节点结果："
                + RuntimePayloads.toJson(objectMapper, results);
        String payload = RuntimePayloads.toJson(objectMapper, Map.of("message", message));
        String commandId = UUID.randomUUID().toString();
        commandMapper.insertSubmit(commandId, job.userId(), "worker-job:" + job.jobId(),
                "plan:" + job.planVersion() + ":" + job.nodeKey(), ContentDigests.sha256(payload),
                job.taskId(), bindingId, payload, now);
        log.info("依赖 Worker 已激活 taskId={} jobId={} bindingId={} commandId={} dependencyCount={}",
                job.taskId(), job.jobId(), bindingId, commandId, dependencies.size());
    }

    private JsonNode readJson(String value) {
        try {
            return RuntimePayloads.readJson(objectMapper, value);
        } catch (Exception exception) {
            throw new IllegalStateException("计划节点固定数据无法读取", exception);
        }
    }
}
